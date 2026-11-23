package io.github.pfwikis.tile;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.zip.GZIPOutputStream;

import com.carrotsearch.hppc.LongLongHashMap;

import io.github.pfwikis.util.Jackson;

/**
 * Minimal PMTiles v3 writer for MLT tiles. Tiles must be added in ascending
 * tile-id order. Tile entries are spooled to a temporary file and directories
 * are built by streaming that spool, so memory use is independent of the number
 * of output tiles.
 */
final class PmtilesWriter implements AutoCloseable {
	private static final int HEADER_OFFSET = 127;
	private static final int INITIAL_SECTION = 16384;

	private final Path output;
	private final FileChannel channel;
	private final BufferedOutputStream tileOut;
	private final LongLongHashMap hashToOffset = new LongLongHashMap();
	private final Path entrySpool;
	private final DataOutputStream entries;
	private final int minLeafSize;
	private final int maxSingleDirectorySize;
	private long lastFlushedTileId;
	private long lastFlushedOffset = -1;
	private long lastFlushedLength;
	private long entryCount;
	private long addressedTiles;
	private long currentOffset;
	private Entry pending;

	PmtilesWriter(Path output) throws IOException {
		this(output,
			Integer.getInteger("tile.pmtilesLeafSize", 262144),
			Integer.getInteger("tile.pmtilesDirectoryThreshold", 16257));
	}

	/** Visible for tests that need to force multiple directory leaves. */
	PmtilesWriter(Path output, int minLeafSize, int maxSingleDirectorySize) throws IOException {
		this.output = output;
		this.minLeafSize = minLeafSize;
		this.maxSingleDirectorySize = maxSingleDirectorySize;
		this.entrySpool = output.resolveSibling(output.getFileName() + ".entries");
		this.entries = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(entrySpool), 1 << 20));
		channel = FileChannel.open(output,
			StandardOpenOption.CREATE,
			StandardOpenOption.READ,
			StandardOpenOption.WRITE,
			StandardOpenOption.TRUNCATE_EXISTING
		);
		var placeholder = ByteBuffer.allocate(INITIAL_SECTION);
		while(placeholder.hasRemaining())
			channel.write(placeholder);
		channel.position(INITIAL_SECTION);
		tileOut = new BufferedOutputStream(Channels.newOutputStream(channel), 1 << 20);
	}

	void add(long tileId, byte[] data) throws IOException {
		add(tileId, data, 0L);
	}

	void add(long tileId, byte[] data, long knownHash) throws IOException {
		addRun(tileId, 1, data, knownHash);
	}

	/** Adds {@code count} consecutive tiles that all have the same content. */
	void addRun(long tileId, int count, byte[] data, long knownHash) throws IOException {
		addressedTiles += count;
		long hash = knownHash != 0L ? knownHash : hash(data);
		long stored = hashToOffset.get(hash);
		long offset;
		if(stored != 0L) {
			offset = stored - 1L;
			if(pending != null && pending.tileId + pending.runLength == tileId && pending.offset == offset) {
				pending = new Entry(pending.tileId, pending.offset, pending.length, pending.runLength + count);
				return;
			}
		} else {
			offset = currentOffset;
			hashToOffset.put(hash, offset + 1L);
			tileOut.write(data);
			currentOffset += data.length;
		}
		flushPending();
		pending = new Entry(tileId, offset, data.length, count);
		entryCount++;
	}

	private void flushPending() throws IOException {
		if(pending == null)
			return;
		TileStore.writeVarLong(entries, pending.tileId - lastFlushedTileId);
		TileStore.writeVarLong(entries, pending.runLength);
		TileStore.writeVarLong(entries, pending.length);
		boolean contiguous = lastFlushedOffset != -1
			&& pending.offset == lastFlushedOffset + lastFlushedLength;
		TileStore.writeVarLong(entries, contiguous ? 0L : pending.offset + 1L);
		lastFlushedTileId = pending.tileId;
		lastFlushedOffset = pending.offset;
		lastFlushedLength = pending.length;
		pending = null;
	}

	void finish(int maxZoom, Map<String, Set<String>> layerFields) throws IOException {
		flushPending();
		entries.close();
		var directories = makeDirectories();
		var metadata = metadata(layerFields);
		var metadataBytes = gzip(Jackson.JSON.writeValueAsBytes(metadata));
		tileOut.write(metadataBytes);
		tileOut.write(directories.leaves);
		tileOut.flush();

		var header = new Header(
			(byte)3,
			HEADER_OFFSET,
			directories.root.length,
			INITIAL_SECTION + currentOffset,
			metadataBytes.length,
			INITIAL_SECTION + currentOffset + metadataBytes.length,
			directories.leaves.length,
			INITIAL_SECTION,
			currentOffset,
			addressedTiles,
			entryCount,
			hashToOffset.size(),
			true,
			(byte)2,
			(byte)2,
			(byte)6,
			(byte)0,
			(byte)maxZoom,
			-1_800_000_000,
			-850_511_287,
			1_800_000_000,
			850_511_287,
			(byte)0,
			0,
			0
		).toBytes();
		channel.write(ByteBuffer.wrap(header), 0);
		channel.write(ByteBuffer.wrap(directories.root), HEADER_OFFSET);
	}

	@Override
	public void close() throws IOException {
		tileOut.close();
		channel.close();
		Files.deleteIfExists(entrySpool);
	}

	private Directories makeDirectories() throws IOException {
		if(entryCount < 16384) {
			var all = readEntries(0, (int)entryCount);
			var test = gzip(directoryToBytes(all));
			if(test.length < maxSingleDirectorySize)
				return new Directories(test, new byte[0]);
		}

		int leafSize = (int)Math.max(minLeafSize, entryCount / 2000L);
		var rootEntries = new ArrayList<Entry>();
		var leaves = new ByteArrayOutputStream();
		long read = 0;
		// The spooled tile-id and offset deltas are relative to the previous
		// entry. The first entry of a leaf must therefore continue from the last
		// entry of the previous leaf; restarting at zero corrupts every later leaf.
		long nextTileId = 0;
		long nextOffset = -1;
		long nextLength = 0;
		try(var in = new DataInputStream(new BufferedInputStream(Files.newInputStream(entrySpool), 1 << 20))) {
			while(read < entryCount) {
				int size = (int)Math.min(leafSize, entryCount - read);
				var chunk = readEntries(in, size, nextTileId, nextOffset, nextLength);
				var leaf = gzip(directoryToBytes(chunk));
				rootEntries.add(new Entry(chunk.getFirst().tileId, leaves.size(), leaf.length, 0));
				leaves.writeBytes(leaf);
				read += size;
				// Deltas are relative to the entry's start, not to the end of its run.
				var last = chunk.getLast();
				nextTileId = last.tileId;
				nextOffset = last.offset;
				nextLength = last.length;
			}
		}
		var root = gzip(directoryToBytes(rootEntries));
		return new Directories(root, leaves.toByteArray());
	}

	private List<Entry> readEntries(int ignored, int count) throws IOException {
		try(var in = new DataInputStream(new BufferedInputStream(Files.newInputStream(entrySpool), 1 << 20))) {
			return readEntries(in, count, 0, -1, 0);
		}
	}

	private List<Entry> readEntries(DataInputStream in, int count, long initialTileId, long initialOffset, long initialLength) throws IOException {
		var result = new ArrayList<Entry>(count);
		long tileId = initialTileId;
		long lastOffset = initialOffset;
		long lastLength = initialLength;
		for(int i=0; i<count; i++) {
			try {
				tileId += TileStore.readVarLong(in);
			} catch(EOFException e) {
				break;
			}
			long runLength = TileStore.readVarLong(in);
			long length = TileStore.readVarLong(in);
			long offsetCode = TileStore.readVarLong(in);
			long offset = offsetCode == 0L ? lastOffset + lastLength : offsetCode - 1L;
			result.add(new Entry(tileId, offset, length, runLength));
			lastOffset = offset;
			lastLength = length;
		}
		return result;
	}

	private static Map<String, Object> metadata(Map<String, Set<String>> layerFields) {
		var layers = new ArrayList<Map<String, Object>>();
		for(var entry : new TreeMap<>(layerFields).entrySet()) {
			var fields = new TreeMap<String, String>();
			for(String field : new TreeSet<>(entry.getValue())) {
				fields.put(field, "String");
			}
			var layer = new TreeMap<String, Object>();
			layer.put("id", entry.getKey());
			layer.put("fields", fields);
			layers.add(layer);
		}
		var metadata = new TreeMap<String, Object>();
		metadata.put("vector_layers", layers);
		return metadata;
	}

	private static byte[] directoryToBytes(List<Entry> list) {
		var output = new ByteArrayOutputStream();
		putVarLong(output, list.size());
		long lastId = 0;
		for(var entry : list) {
			putVarLong(output, entry.tileId - lastId);
			lastId = entry.tileId;
		}
		for(var entry : list)
			putVarLong(output, entry.runLength);
		for(var entry : list)
			putVarLong(output, entry.length);
		Entry last = null;
		for(var entry : list) {
			putVarLong(output, last != null && entry.offset == last.offset + last.length ? 0 : entry.offset + 1);
			last = entry;
		}
		return output.toByteArray();
	}

	private static void putVarLong(ByteArrayOutputStream output, long value) {
		while(true) {
			int bits = (int)value & 0x7F;
			value >>>= 7;
			if(value == 0) {
				output.write(bits);
				return;
			}
			output.write(bits | 0x80);
		}
	}

	private static byte[] gzip(byte[] input) throws IOException {
		var output = new ByteArrayOutputStream(input.length);
		try(var gzip = new GZIPOutputStream(output)) {
			gzip.write(input);
		}
		return output.toByteArray();
	}

	static long hash(byte[] bytes) {
		long hash = 0xcbf29ce484222325L;
		for(byte b : bytes) {
			hash ^= b & 0xFF;
			hash *= 0x100000001b3L;
		}
		return hash;
	}

	private record Entry(long tileId, long offset, long length, long runLength) {}

	private record Directories(byte[] root, byte[] leaves) {}

	private record Header(
		byte specVersion,
		long rootDirOffset,
		long rootDirLength,
		long jsonMetadataOffset,
		long jsonMetadataLength,
		long leafDirectoriesOffset,
		long leafDirectoriesLength,
		long tileDataOffset,
		long tileDataLength,
		long numAddressedTiles,
		long numTileEntries,
		long numTileContents,
		boolean clustered,
		byte internalCompression,
		byte tileCompression,
		byte tileType,
		byte minZoom,
		byte maxZoom,
		int minLonE7,
		int minLatE7,
		int maxLonE7,
		int maxLatE7,
		byte centerZoom,
		int centerLonE7,
		int centerLatE7
	) {
		byte[] toBytes() {
			var buffer = ByteBuffer.allocate(127).order(ByteOrder.LITTLE_ENDIAN);
			buffer.put("PMTiles".getBytes(StandardCharsets.UTF_8));
			buffer.put(specVersion);
			buffer.putLong(rootDirOffset);
			buffer.putLong(rootDirLength);
			buffer.putLong(jsonMetadataOffset);
			buffer.putLong(jsonMetadataLength);
			buffer.putLong(leafDirectoriesOffset);
			buffer.putLong(leafDirectoriesLength);
			buffer.putLong(tileDataOffset);
			buffer.putLong(tileDataLength);
			buffer.putLong(numAddressedTiles);
			buffer.putLong(numTileEntries);
			buffer.putLong(numTileContents);
			buffer.put((byte)(clustered ? 1 : 0));
			buffer.put(internalCompression);
			buffer.put(tileCompression);
			buffer.put(tileType);
			buffer.put(minZoom);
			buffer.put(maxZoom);
			buffer.putInt(minLonE7);
			buffer.putInt(minLatE7);
			buffer.putInt(maxLonE7);
			buffer.putInt(maxLatE7);
			buffer.put(centerZoom);
			buffer.putInt(centerLonE7);
			buffer.putInt(centerLatE7);
			return buffer.array();
		}
	}
}
