export function decompress(input:ArrayBuffer):Promise<ArrayBuffer> {
    const ds = new DecompressionStream("gzip");
    const writer = ds.writable.getWriter();
    writer.write(input);
    writer.close();
    return new Response(ds.readable).arrayBuffer();
}