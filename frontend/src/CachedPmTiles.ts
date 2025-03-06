import { DBSchema, openDB, deleteDB, IDBPDatabase } from "idb";
import { FetchSource, RangeResponse, Source } from "pmtiles";

const buildId = parseInt(import.meta.env.VITE_DATA_HASH)||0;
const dbId = `map-db-${buildId}`;
const ranges = 'ranges';
type Key = IDBValidKey&[number, number]

interface Schema extends DBSchema {
  ranges: {
    key: Key;
    value: ArrayBuffer;
  };
}

const dbPromise=openDB<Schema>(dbId, 1, {
  upgrade(database, oldVersion, newVersion, transaction, event) {
    database.createObjectStore(ranges)
  },
});

setTimeout(async ()=>{
  let databases = await indexedDB.databases()
  databases.forEach(other => {
    if(other.name.startsWith('map-db-')) {
      try {
        let otherId = parseInt(other.name.substring(7));
        if(otherId < buildId) {
          console.log(`Deleting old version ${otherId} as the new version is ${buildId}`);
          deleteDB(other.name)
        }
        else {
          console.log(`Can't delete version ${otherId} as the my version is ${buildId}`);
        }
      } catch(e) {
        console.error(e);
      }
    }
  })
}, 5000);


export class CachedSource implements Source {
  fetcher: FetchSource;

  constructor(url:string) {
    this.fetcher = new FetchSource(url);
    this.getBytes = buildId?this.waitLoadCacheStore:this.waitLoadWebStore;

    dbPromise.then(db => {
      //we want to try loading from the cache first, if we are not in a DEV scenario
      if(buildId)
        this.getBytes = (offset: number, length: number, signal?: AbortSignal, etag?: string) => this.loadCacheStore(db, offset, length, signal, etag);
      else
        this.getBytes = (offset: number, length: number, signal?: AbortSignal, etag?: string) => this.loadWebStore(db, offset, length, signal, etag);
    }).catch(e => {
      //if the db fails we load directly from web
      this.getBytes = this.loadWeb;
    });
  }

  getBytes: (offset: number, length: number, signal?: AbortSignal, etag?: string) => Promise<RangeResponse>;

  key(offset: number, length: number):Key {
    return [offset, length]
  }

  loadCacheStore(db:IDBPDatabase<Schema>, offset: number, length: number, signal?: AbortSignal, etag?: string):Promise<RangeResponse> {
    return db.get(ranges, this.key(offset, length))
      .catch(e=>{
        console.log(e);
        this.loadWeb(offset, length, signal, etag);
      })
      .then(content=>{
        if(content)
          return {data:content} as RangeResponse
        return this.loadWebStore(db, offset, length, signal, etag);
      });
  }

  loadWebStore(db:IDBPDatabase<Schema>, offset: number, length: number, signal?: AbortSignal, etag?: string):Promise<RangeResponse> {
    return this.loadWeb(offset, length, signal, etag).then(resp => {
      db.put(ranges, resp.data, this.key(offset, length));
      return resp;
    });
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