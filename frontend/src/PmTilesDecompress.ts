import { Compression, type DecompressFunc } from 'pmtiles';
import { decompressSync, inflateSync } from 'fflate';

function copyToArrayBuffer(data: Uint8Array): ArrayBuffer {
  const copy = new Uint8Array(data.length);
  copy.set(data);
  return copy.buffer;
}

// Tile payloads use raw DEFLATE to avoid the 18-byte gzip wrapper on every tile;
// internal directories and metadata remain gzip.
export const pmtilesDecompress: DecompressFunc = async (buf, compression) => {
  if(compression === Compression.Unknown)
    return copyToArrayBuffer(inflateSync(new Uint8Array(buf)));
  if(compression === Compression.None)
    return buf;
  if(compression === Compression.Gzip) {
    if(typeof DecompressionStream === 'undefined')
      return copyToArrayBuffer(decompressSync(new Uint8Array(buf)));
    const stream = new Response(buf).body;
    if(!stream)
      throw new Error('Failed to read response stream');
    return new Response(stream.pipeThrough(new DecompressionStream('gzip'))).arrayBuffer();
  }
  throw new Error(`Compression method not supported: ${compression}`);
};
