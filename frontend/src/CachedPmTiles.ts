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
    if(other.name !== dbId) {
      deleteDB(other.name)
    }
  })
}, 5000);


export class CachedSource implements Source {
  fetcher: FetchSource;

  constructor(url:string) {
    this.fetcher = new FetchSource(url);
    this.getBytes = this.waitToLoad; //at first we just redirect loads to the fetcher

    dbPromise.then(db => {
      //as soon as the cache runs we want to load and store
      this.getBytes = this.load = (offset: number, length: number, signal?: AbortSignal, etag?: string) => this.loadAndStore(db, offset, length, signal, etag);
      //and we want to try loading from the cache first, if we are not in a DEV scenario
      if(buildId)
        this.getBytes = (offset: number, length: number, signal?: AbortSignal, etag?: string) => this.loadCachedOrFresh(db, offset, length, signal, etag);
    }).catch(e => {
      //if the db fails we load directly from web
      this.getBytes = this.load;
    });
  }

  getBytes: (offset: number, length: number, signal?: AbortSignal, etag?: string) => Promise<RangeResponse>;

  key(offset: number, length: number):Key {
    return [offset, length]
  }

  loadCachedOrFresh(db:IDBPDatabase<Schema>, offset: number, length: number, signal?: AbortSignal, etag?: string):Promise<RangeResponse> {
    return db.get(ranges, this.key(offset, length))
      .catch(e=>this.load(offset, length, signal, etag))
      .then(content=>{
        if(content)
          return {data:content} as RangeResponse
        return this.load(offset, length, signal, etag)
      });
  }

  loadAndStore(db:IDBPDatabase<Schema>, offset: number, length: number, signal?: AbortSignal, etag?: string):Promise<RangeResponse> {
    return this.fetcher.getBytes(offset, length, signal, etag).then(resp => {
      db.put(ranges, resp.data, this.key(offset, length));
      return resp;
    });
  }

  waitToLoad(offset: number, length: number, signal?: AbortSignal, etag?: string):Promise<RangeResponse> {
    return dbPromise.then(db=>this.loadCachedOrFresh(db, offset, length, signal, etag));
  }

  load(offset: number, length: number, signal?: AbortSignal, etag?: string):Promise<RangeResponse> {
    return this.fetcher.getBytes(offset, length, signal, etag);
  }

  getKey() {
    return this.fetcher.getKey();
  }
}