import { FetchSource, RangeResponse, Source } from "pmtiles";

const buildId = parseInt(import.meta.env.VITE_DATA_HASH)||0;
const dbId = `map-db-${buildId}`;
const ranges = 'ranges';
type Key = IDBValidKey&[number, number]

const dbPromise = navigator.storage.getDirectory()
  .then(fapi => fapi.getDirectoryHandle(dbId, {create:true}));

setTimeout(async ()=>{
  let api = await navigator.storage.getDirectory()
  for await (const handle of api.values()) {
    if(handle.name.startsWith('map-db-')) {
      try {
        let otherId = parseInt(handle.name.substring(7));
        if(otherId < buildId) {
          console.log(`Deleting old version ${otherId} as the new version is ${buildId}`);
          api.removeEntry(handle.name, {recursive: true});
        }
        else {
          console.log(`Can't delete version ${otherId} as the my version is ${buildId}`);
        }
      } catch(e) {
        console.error(e);
      }
    }
  }
}, 5000);


export class CachedSource implements Source {
  fetcher: FetchSource;

  constructor(url:string) {
    this.fetcher = new FetchSource(url);
    let useCache = Boolean(buildId)
    this.getBytes = useCache?this.waitLoadCacheStore:this.waitLoadWebStore;

    dbPromise.then(db => {
      //we want to try loading from the cache first, if we are not in a DEV scenario
      if(useCache)
        this.getBytes = (offset: number, length: number, signal?: AbortSignal, etag?: string) => this.loadCacheStore(db, offset, length, signal, etag);
      else
        this.getBytes = (offset: number, length: number, signal?: AbortSignal, etag?: string) => this.loadWebStore(db, offset, length, signal, etag);
    }).catch(e => {
      //if the db fails we load directly from web
      this.getBytes = this.loadWeb;
      console.log(e)
    });
  }

  getBytes: (offset: number, length: number, signal?: AbortSignal, etag?: string) => Promise<RangeResponse>;

  key(offset: number, length: number):string {
    return `${offset}-${length}`;
  }

  loadCacheStore(db:FileSystemDirectoryHandle, offset: number, length: number, signal?: AbortSignal, etag?: string):Promise<RangeResponse> {
    return db.getFileHandle(this.key(offset, length))
      .catch(e=>{
        if(e instanceof DOMException && e.name == 'NotFoundError') {
          return this.loadWebStore(db, offset, length, signal, etag);
        }
        else {
          console.log(e);
          this.loadWeb(offset, length, signal, etag);
        }
      })
      .then(f=>{
        if(f instanceof FileSystemFileHandle) {
          return f.getFile()
            .then(fc=>fc.arrayBuffer())
            .then(d=> {
              return {data:d};
            })
        }
        return f;
      })
      
  }

  loadWebStore(db:FileSystemDirectoryHandle, offset: number, length: number, signal?: AbortSignal, etag?: string):Promise<RangeResponse> {
    let loaded = this.loadWeb(offset, length, signal, etag)
    loaded.then(async resp => {
      let fh = await db.getFileHandle(this.key(offset, length), {create:true})
      let w = await fh.createWritable()
      await w.write(resp.data)
      await w.close()
      
    });
    return loaded
  }

  waitLoadWebStore(offset: number, length: number, signal?: AbortSignal, etag?: string):Promise<RangeResponse> {
    return dbPromise.then(db=>this.loadWebStore(db, offset, length, signal, etag));
  }

  waitLoadCacheStore(offset: number, length: number, signal?: AbortSignal, etag?: string):Promise<RangeResponse> {
    return dbPromise.then(db=>this.loadCacheStore(db, offset, length, signal, etag));
  }

  loadWeb(offset: number, length: number, signal?: AbortSignal, etag?: string):Promise<RangeResponse> {
    return this.fetcher.getBytes(offset, length, signal, etag);
  }

  getKey() {
    return this.fetcher.getKey();
  }
}