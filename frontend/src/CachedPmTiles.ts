import { DBSchema, openDB } from "idb";
import { FetchSource, RangeResponse, Source } from "pmtiles";

const dbId = 'map.pathfinderwiki.com';
const rangesV1 = 'rangesV1';
type Key = IDBValidKey&[number, number, number]
interface Schema extends DBSchema {
  rangesV1: {
    key: Key;
    value: ArrayBuffer;
  };
}

const db = await openDB<Schema>(dbId, 1, {
  upgrade(database, oldVersion, newVersion, transaction, event) {
    database.createObjectStore(rangesV1)
  },
})

export class CachedSource implements Source {
  fetcher: FetchSource;
  buildId: number;

  constructor(url:string, buildId?:number) {
    this.buildId = buildId||0;
    this.fetcher = new FetchSource(url);
    //if there is no number buildId disable loading from cache (for dev purposes)
    if(!buildId)
      this.getBytes = this.load;

    //clean database of older versions after 10s
    setTimeout(async ()=> {
      let keyCursor = await db.transaction(rangesV1).store.openKeyCursor()
      while(keyCursor) {
        if(keyCursor.key[0] !== buildId) {
          db.delete(rangesV1, keyCursor.primaryKey)
          //store.delete(keyCursor.primaryKey)
          //console.log("Deleting old cache entry "+keyCursor.key)
        }
        keyCursor = await keyCursor.continue()
      }
      //tx.commit()
    }, 10000)
  }

  key(offset: number, length: number):Key {
    return [this.buildId, offset, length]
  }

  getBytes(offset: number, length: number, signal?: AbortSignal, etag?: string):Promise<RangeResponse> {
    return db.get(rangesV1, this.key(offset, length))
      .catch(e=>this.load(offset, length, signal, etag))
      .then(content=>{
        if(content)
          return {data:content} as RangeResponse
        return this.load(offset, length, signal, etag)
      });
  }

  load(offset: number, length: number, signal?: AbortSignal, etag?: string):Promise<RangeResponse> {
    return this.fetcher.getBytes(offset, length, signal, etag).then(resp => {
      db.put(rangesV1, resp.data, this.key(offset, length));
      return resp;
    });
  }

  getKey() {
    return this.fetcher.getKey();
  }
}