import { FetchSource, RangeResponse, Source } from "pmtiles";

const buildId = BUILD_DATA_HASH;
const dbId = `map-db-${buildId}`;
const ranges = 'ranges';
type Key = IDBValidKey&[number, number]

setTimeout(async ()=>{
  let api = await navigator.storage.getDirectory()
  for await (const handle of api.values()) {
    if(handle.name.startsWith('map-db-')) {
      try {
        let otherId = parseInt(handle.name.substring(7));
        if(buildId === 'latest' || otherId < buildId) {
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
  dbPromise: Promise<FileSystemDirectoryHandle> | undefined;
  private useCache = buildId !== 'latest';
  private pendingWrites = new Set<string>();

  constructor(url:string) {
    //if this url does not match the one in style we do not cache!!!
    if(url.startsWith('pmtiles://'))
      url = url.substring(10);

    this.fetcher = new FetchSource(url);
    this.getBytes = this.loadWeb.bind(this);

    if(this.useCache) {
      this.getBytes = this.waitLoadCacheStore.bind(this);
      this.dbPromise = navigator.storage.getDirectory().then(fapi => fapi.getDirectoryHandle(dbId, {create:true}));

      this.dbPromise.then(db => {
        //we want to try loading from the cache first
        this.getBytes = (offset: number, length: number, signal?: AbortSignal, etag?: string) => this.loadCacheStore(db, offset, length, signal, etag);
      }).catch(e => {
        //if the db fails we load directly from web
        this.getBytes = this.loadWeb.bind(this);
        console.log(e)
      });
    }
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
          return this.loadWeb(offset, length, signal, etag);
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
    const key = this.key(offset, length);

    //a concurrent request is already caching this range
    if(this.pendingWrites.has(key))
      return loaded;

    this.pendingWrites.add(key);
    void loaded.then(async resp => {
      if(signal?.aborted)
        return;
      try {
        let fh = await db.getFileHandle(key, {create:true})
        let w = await fh.createWritable()
        await w.write(resp.data)
        await w.close()
      }
      catch(e) {
        console.warn(`Failed to cache tile range ${key}`, e);
      }
    }, () => {
      //the web request failed, so there is nothing to cache
    }).finally(() => this.pendingWrites.delete(key));

    return loaded
  }

  waitLoadCacheStore(offset: number, length: number, signal?: AbortSignal, etag?: string):Promise<RangeResponse> {
    return this.dbPromise!.then(db=>this.loadCacheStore(db, offset, length, signal, etag));
  }

  loadWeb(offset: number, length: number, signal?: AbortSignal, etag?: string):Promise<RangeResponse> {
    return this.fetcher.getBytes(offset, length, signal, etag);
  }

  getKey() {
    return this.fetcher.getKey();
  }
}