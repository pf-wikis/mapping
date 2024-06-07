import { DBSchema, openDB, deleteDB } from "idb";
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

const db = await openDB<Schema>(dbId, 1, {
  upgrade(database, oldVersion, newVersion, transaction, event) {
    database.createObjectStore(ranges)
  },
})

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
    //if there is no number buildId disable loading from cache (for dev purposes)
    if(!buildId)
      this.getBytes = this.load;
  }

  key(offset: number, length: number):Key {
    return [offset, length]
  }

  getBytes(offset: number, length: number, signal?: AbortSignal, etag?: string):Promise<RangeResponse> {
    return db.get(ranges, this.key(offset, length))
      .catch(e=>this.load(offset, length, signal, etag))
      .then(content=>{
        if(content)
          return {data:content} as RangeResponse
        return this.load(offset, length, signal, etag)
      });
  }

  load(offset: number, length: number, signal?: AbortSignal, etag?: string):Promise<RangeResponse> {
    return this.fetcher.getBytes(offset, length, signal, etag).then(resp => {
      db.put(ranges, resp.data, this.key(offset, length));
      return resp;
    });
  }

  getKey() {
    return this.fetcher.getKey();
  }
}