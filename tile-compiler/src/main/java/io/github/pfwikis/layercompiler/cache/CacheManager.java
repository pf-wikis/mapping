package io.github.pfwikis.layercompiler.cache;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.jar.JarFile;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;

import io.github.pfwikis.layercompiler.description.Ctx;
import io.github.pfwikis.layercompiler.description.StepDescription;
import io.github.pfwikis.layercompiler.steps.model.StepExecutor;
import io.github.pfwikis.layercompiler.steps.model.content.Content;
import io.github.pfwikis.layercompiler.steps.model.content.MergedOrTimelessContent;
import io.github.pfwikis.layercompiler.steps.model.content.TimeSlicedContent;
import io.github.pfwikis.layercompiler.steps.model.data.GeoData;
import io.github.pfwikis.run.ToolVariant;
import io.github.pfwikis.util.Jackson;
import io.github.pfwikis.util.time.TimeRange;
import lombok.extern.slf4j.Slf4j;

/**
 * Persistent, content addressed cache for step results.
 *
 * <p>The cache directory contains {@code entries/} metadata files and {@code blobs/} data files.
 * Every cache file that is read or written during a successful run is marked. At the end of the run
 * all unmarked files are removed, so the cache does not grow after data or code changes.</p>
 */
@Slf4j
public enum CacheManager {

	INSTANCE;

	private static final String CACHE_FORMAT = "1";
	private static final String ENTRIES = "entries";
	private static final String BLOBS = "blobs";
	public static final String BUILD_CLASS_HASH_PATH = "io/github/pfwikis/cache/build-class-hash.txt";

	private final Set<Path> touched = ConcurrentHashMap.newKeySet();
	private final ConcurrentMap<Path, byte[]> fileHashes = new ConcurrentHashMap<>();
	private final AtomicLong hits = new AtomicLong();
	private final AtomicLong misses = new AtomicLong();
	private final AtomicLong writes = new AtomicLong();

	private boolean enabled;
	private Path root;
	private Path entriesDir;
	private Path blobsDir;
	private String codeHash;
	private String toolHash;

	public synchronized void initialize(File cacheDirectory, boolean disabled) {
		enabled = false;
		root = null;
		entriesDir = null;
		blobsDir = null;
		codeHash = null;
		toolHash = null;
		touched.clear();
		fileHashes.clear();
		hits.set(0);
		misses.set(0);
		writes.set(0);

		if(disabled || cacheDirectory == null) {
			log.info("Persistent cache disabled");
			return;
		}

		root = cacheDirectory.toPath().toAbsolutePath().normalize();
		try {
			codeHash = computeCodeHash();
			toolHash = computeToolHash();
		} catch(Exception e) {
			throw new IllegalStateException("Could not compute tile-compiler cache hashes", e);
		}

		try {
			entriesDir = root.resolve(ENTRIES);
			blobsDir = root.resolve(BLOBS);
			Files.createDirectories(entriesDir);
			Files.createDirectories(blobsDir);
			enabled = true;
			log.info("Persistent cache enabled at {} (code hash {})", root, codeHash);
			log.debug("External tool fingerprint:\n{}", toolHash);
		} catch(IOException e) {
			log.error("Could not create persistent cache at {}; continuing without cache", root, e);
		}
	}

	public boolean isEnabled() {
		return enabled;
	}

	/**
	 * Returns the cache key for a step, or {@code null} if caching is disabled.
	 */
	public String keyFor(StepExecutor step) {
		if(!enabled || !step.isCacheable())
			return null;

		var description = step.getDescription();
		var digest = sha256();
		updateString(digest, CACHE_FORMAT);
		updateString(digest, codeHash);
		updateString(digest, toolHash);
		updateString(digest, description.getId());
		updateString(digest, description.getGroup());
		updateString(digest, description.getStep());
		updateString(digest, description.getExecutor().getClass().getName());
		updateString(digest, description.getCacheConfig());
		updateString(digest, Integer.toString(Ctx.INSTANCE.getOptions().getMaxZoom()));
		updateString(digest, Boolean.toString(Ctx.INSTANCE.getOptions().isProdDetail()));

		var dataHashes = new IdentityHashMap<GeoData, byte[]>();
		for(var input : step.getInputsForCaching()) {
			updateString(digest, input.getKey());
			updateContent(digest, input.getValue(), dataHashes);
		}

		var salt = step.getCacheSalt();
		if(salt != null)
			updateBytes(digest, salt);

		return HexFormat.of().formatHex(digest.digest());
	}

	public Content load(String key) {
		if(!enabled)
			return null;

		var entry = entryPath(key);
		if(!Files.isRegularFile(entry)) {
			misses.incrementAndGet();
			return null;
		}

		try {
			var cached = Jackson.JSON.readValue(entry.toFile(), CachedContent.class);
			if(cached == null || !CACHE_FORMAT.equals(cached.format()) || cached.state() == null)
				throw new IOException("Invalid cache metadata");

			var content = switch(cached.state()) {
				case "MERGED" -> Content.merged(loadMergedOrTimeless(cached));
				case "TIMELESS" -> Content.timeless(loadMergedOrTimeless(cached));
				case "SLICED" -> Content.sliced(loadSliced(cached));
				default -> throw new IOException("Unknown cache content state "+cached.state());
			};

			mark(entry);
			hits.incrementAndGet();
			log.debug("Cache hit for {}", key);
			return content;
		} catch(Exception e) {
			misses.incrementAndGet();
			log.warn("Ignoring invalid cache entry {}: {}", entry, e.getMessage());
			return null;
		}
	}

	public void store(String key, Content content) {
		if(!enabled || !isCacheable(content))
			return;

		try {
			var slices = new ArrayList<CachedSlice>();
			if(content instanceof MergedOrTimelessContent merged) {
				slices.add(toCachedSlice(null, null, merged.getData()));
			}
			else if(content instanceof TimeSlicedContent sliced) {
				for(var slice : sliced.getSlices()) {
					slices.add(toCachedSlice(
						slice.getTime().getTimeStart(),
						slice.getTime().getTimeEnd(),
						slice.getData()
					));
				}
			}
			else {
				throw new IOException("Unsupported content type "+content.getClass().getName());
			}

			var cached = new CachedContent(
				CACHE_FORMAT,
				content.getTimeState().name(),
				slices
			);
			var entry = entryPath(key);
			writeJsonAtomically(entry, cached);
			mark(entry);
			writes.incrementAndGet();
			log.debug("Stored cache entry {}", key);
		} catch(Exception e) {
			log.warn("Could not store cache entry for {}: {}", key, e.getMessage());
		}
	}

	/**
	 * Removes all cache files that were not read or written during this run.
	 * Call only after a successful run.
	 */
	public synchronized void sweep() {
		if(!enabled)
			return;

		try {
			long deleted = sweepDirectory(entriesDir) + sweepDirectory(blobsDir);
			log.info(
				"Persistent cache: {} hits, {} misses, {} writes, {} unused files deleted",
				hits.get(),
				misses.get(),
				writes.get(),
				deleted
			);
		} catch(Exception e) {
			log.warn("Could not clean up persistent cache at {}", root, e);
		} finally {
			touched.clear();
			fileHashes.clear();
		}
	}

	/**
	 * Returns a stable hash of a file's content. Used by steps that read external inputs.
	 */
	public byte[] hashFile(Path path) {
		var absolute = path.toAbsolutePath().normalize();
		return fileHashes.computeIfAbsent(absolute, p -> {
			var digest = sha256();
			try(var in = Files.newInputStream(p)) {
				var buffer = new byte[64 * 1024];
				int read;
				while((read = in.read(buffer)) >= 0) {
					if(read > 0)
						digest.update(buffer, 0, read);
				}
			} catch(IOException e) {
				throw new UncheckedIOException("Could not hash "+p, e);
			}
			return digest.digest();
		});
	}

	private boolean isCacheable(Content content) {
		if(content == Content.EMPTY)
			return false;
		if(content instanceof MergedOrTimelessContent merged)
			return !merged.getData().isEmpty();
		if(content instanceof TimeSlicedContent sliced)
			return !sliced.getSlices().isEmpty();
		return false;
	}

	private GeoData loadMergedOrTimeless(CachedContent cached) throws IOException {
		if(cached.slices().size() != 1)
			throw new IOException("Expected exactly one data entry");
		var data = loadData(cached.slices().getFirst());
		if(data.isEmpty())
			throw new IOException("Merged or timeless cache entries must not be empty");
		return data;
	}

	private List<TimeSlicedContent.TimeSlice> loadSliced(CachedContent cached) throws IOException {
		var result = new ArrayList<TimeSlicedContent.TimeSlice>(cached.slices().size());
		for(var slice : cached.slices()) {
			result.add(new TimeSlicedContent.TimeSlice(
				new TimeRange(slice.start(), slice.end()),
				loadData(slice)
			));
		}
		return result;
	}

	private GeoData loadData(CachedSlice slice) throws IOException {
		if(slice.empty())
			return GeoData.empty();
		if(slice.blob() == null)
			throw new IOException("Missing blob hash");
		var blob = blobPath(slice.blob());
		if(!Files.isRegularFile(blob))
			throw new IOException("Missing cache blob "+blob);
		mark(blob);
		return GeoData.from(blob);
	}

	private CachedSlice toCachedSlice(Integer start, Integer end, GeoData data) throws IOException {
		if(data.isEmpty())
			return new CachedSlice(start, end, true, null);

		var bytes = data.toBytes();
		var hash = sha256Hex(bytes);
		var blob = blobPath(hash);
		if(!Files.isRegularFile(blob))
			writeBytesAtomically(blob, bytes);
		mark(blob);
		return new CachedSlice(start, end, false, hash);
	}

	private void writeBytesAtomically(Path target, byte[] bytes) throws IOException {
		var tmp = Files.createTempFile(target.getParent(), ".tmp-", ".blob");
		boolean moved = false;
		try {
			Files.write(tmp, bytes);
			moveAtomically(tmp, target);
			moved = true;
		} catch(FileAlreadyExistsException e) {
			// Another writer created the same content in the meantime.
			Files.deleteIfExists(tmp);
		} finally {
			if(!moved)
				Files.deleteIfExists(tmp);
		}
	}

	private void writeJsonAtomically(Path target, Object value) throws IOException {
		var tmp = Files.createTempFile(target.getParent(), ".tmp-", ".json");
		boolean moved = false;
		try {
			Jackson.JSON.writeValue(tmp.toFile(), value);
			moveAtomically(tmp, target);
			moved = true;
		} finally {
			if(!moved)
				Files.deleteIfExists(tmp);
		}
	}

	private static void moveAtomically(Path from, Path to) throws IOException {
		try {
			Files.move(from, to, StandardCopyOption.ATOMIC_MOVE);
		} catch(AtomicMoveNotSupportedException | FileAlreadyExistsException e) {
			Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private long sweepDirectory(Path directory) throws IOException {
		if(!Files.isDirectory(directory))
			return 0;

		long deleted = 0;
		try(var paths = Files.walk(directory)) {
			for(var path : paths.sorted(Comparator.reverseOrder()).toList()) {
				if(path.equals(directory))
					continue;
				if(Files.isDirectory(path)) {
					try {
						Files.delete(path);
					} catch(DirectoryNotEmptyException e) {
						// still contains a touched file
					}
				}
				else if(!isTouched(path)) {
					if(Files.deleteIfExists(path))
						deleted++;
				}
			}
		}
		return deleted;
	}

	private boolean isTouched(Path path) {
		return touched.contains(path.toAbsolutePath().normalize());
	}

	private void mark(Path path) {
		touched.add(path.toAbsolutePath().normalize());
	}

	private Path entryPath(String key) {
		return entriesDir.resolve(key+".json");
	}

	private Path blobPath(String hash) {
		return blobsDir.resolve(hash+".geojson");
	}

	private void updateContent(MessageDigest digest, Content content, IdentityHashMap<GeoData, byte[]> dataHashes) {
		updateString(digest, content.getTimeState().name());
		if(content instanceof MergedOrTimelessContent merged) {
			updateData(digest, merged.getData(), dataHashes);
		}
		else if(content instanceof TimeSlicedContent sliced) {
			updateInt(digest, sliced.getSlices().size());
			for(var slice : sliced.getSlices()) {
				updateNullableInt(digest, slice.getTime().getTimeStart());
				updateNullableInt(digest, slice.getTime().getTimeEnd());
				updateData(digest, slice.getData(), dataHashes);
			}
		}
		else {
			throw new IllegalStateException("Unsupported content type "+content.getClass().getName());
		}
	}

	private static void updateData(MessageDigest digest, GeoData data, IdentityHashMap<GeoData, byte[]> dataHashes) {
		if(data.isEmpty()) {
			digest.update((byte)0);
			return;
		}
		digest.update((byte)1);
		var bytes = dataHashes.computeIfAbsent(data, GeoData::toBytes);
		updateInt(digest, bytes.length);
		digest.update(bytes);
	}

	private static void updateNullableInt(MessageDigest digest, Integer value) {
		if(value == null) {
			digest.update((byte)0);
		}
		else {
			digest.update((byte)1);
			updateInt(digest, value);
		}
	}

	private static void updateString(MessageDigest digest, String value) {
		updateBytes(digest, value.getBytes(StandardCharsets.UTF_8));
	}

	private static void updateBytes(MessageDigest digest, byte[] bytes) {
		updateInt(digest, bytes.length);
		digest.update(bytes);
	}

	private static void updateInt(MessageDigest digest, int value) {
		digest.update((byte)(value >>> 24));
		digest.update((byte)(value >>> 16));
		digest.update((byte)(value >>> 8));
		digest.update((byte)value);
	}

	private static MessageDigest sha256() {
		try {
			return MessageDigest.getInstance("SHA-256");
		} catch(NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	private static String sha256Hex(byte[] bytes) {
		return HexFormat.of().formatHex(sha256().digest(bytes));
	}

	/**
	 * Records the versions/fingerprints of every external tool used by the compiler. Tool upgrades
	 * therefore invalidate all cached tool output.
	 */
	private static String computeToolHash() {
		var result = new StringBuilder();
		for(var command : List.of("mapshaper", "qgis_process", "spritezero")) {
			result.append("=== ").append(command).append('\n');
			try {
				var variant = ToolVariant.getFor(command);
				result.append("variant=").append(variant.name()).append('\n');
				result.append(runTool(variant, versionCommand(command))).append('\n');

				// spritezero does not expose a version flag; use the npm package metadata as well.
				if("spritezero".equals(command)) {
					result.append(runTool(variant, List.of(
						"npm", "list", "-g", "@indoorequal/spritezero-cli",
						"--depth=0", "--json"
					))).append('\n');
				}
			} catch(Exception e) {
				result.append("unavailable: ").append(e.getMessage()).append('\n');
			}
		}
		return result.toString();
	}

	private static List<String> versionCommand(String command) {
		return switch(command) {
			case "mapshaper" -> List.of("mapshaper", "--version");
			case "qgis_process" -> List.of("qgis_process", "-v");
			case "spritezero" -> List.of("spritezero", "help");
			default -> List.of(command, "--version");
		};
	}

	private static String runTool(ToolVariant variant, List<String> args) {
		try {
			var command = new ArrayList<>(args);
			variant.modifyArguments(command);
			var process = new ProcessBuilder(command)
				.redirectErrorStream(true)
				.start();
			if(!process.waitFor(30, TimeUnit.SECONDS)) {
				process.destroyForcibly();
				return "timeout";
			}
			var output = IOUtils.toString(process.getInputStream(), StandardCharsets.UTF_8).strip();
			return "exit="+process.exitValue()+"\n"+output;
		} catch(Exception e) {
			return "error: "+e.getMessage();
		}
	}

	/**
	 * Hashes the class files of the compiler plus the pipeline definition. This intentionally
	 * invalidates the entire cache whenever compiler code changes.
	 */
	private static String computeCodeHash() throws IOException, URISyntaxException {
		var digest = sha256();
		updateString(digest, CACHE_FORMAT);

		var location = StepExecutor.class.getProtectionDomain().getCodeSource().getLocation();
		var source = Path.of(location.toURI());
		updateString(digest, "classes");
		updateString(digest, classHashFor(source));

		for(var configFile : List.of("pom.xml", "steps.yaml")) {
			var path = Path.of(configFile);
			if(Files.isRegularFile(path)) {
				updateString(digest, configFile);
				digest.update(Files.readAllBytes(path));
			}
		}

		return HexFormat.of().formatHex(digest.digest());
	}

	private static String classHashFor(Path source) throws IOException {
		var buildHash = readBuildClassHash(source);
		return buildHash != null ? buildHash : computeClassHash(source);
	}

	private static String readBuildClassHash(Path source) throws IOException {
		if(Files.isDirectory(source)) {
			var resource = source.resolve(BUILD_CLASS_HASH_PATH);
			if(!Files.isRegularFile(resource) || !isBuildClassHashFresh(source, resource))
				return null;
			var value = Files.readString(resource).strip();
			if(value.isEmpty())
				return null;
			log.debug("Using build-time class hash from {}", resource);
			return value;
		}

		if(Files.isRegularFile(source)) {
			try(var jar = new JarFile(source.toFile())) {
				var entry = jar.getJarEntry(BUILD_CLASS_HASH_PATH);
				if(entry == null)
					return null;
				try(InputStream in = jar.getInputStream(entry)) {
					var value = new String(in.readAllBytes(), StandardCharsets.UTF_8).strip();
					if(value.isEmpty())
						return null;
					log.debug("Using build-time class hash from JAR entry {}", entry.getName());
					return value;
				}
			}
		}

		return null;
	}

	/*
	 * In an IDE, Maven's generated hash resource can be older than classes compiled by the IDE.
	 * In that case fall back to hashing the classes directly.
	 */
	private static boolean isBuildClassHashFresh(Path source, Path resource) throws IOException {
		var resourceTime = Files.getLastModifiedTime(resource).toMillis();
		var classes = source.resolve("io/github/pfwikis");
		try(var paths = Files.walk(classes)) {
			return paths.filter(Files::isRegularFile)
				.filter(p -> p.toString().endsWith(".class"))
				.allMatch(p -> {
					try {
						return Files.getLastModifiedTime(p).toMillis() <= resourceTime;
					} catch(IOException e) {
						return false;
					}
				});
		}
	}

	/**
	 * Hashes all compiler class files under a classes directory or JAR. Public so the Maven
	 * process-classes step can persist this hash as a resource.
	 */
	public static String computeClassHash(Path source) throws IOException {
		var digest = sha256();
		updateString(digest, CACHE_FORMAT);
		var entries = new ArrayList<String>();

		if(Files.isDirectory(source)) {
			var base = source.resolve("io/github/pfwikis");
			try(var paths = Files.walk(base)) {
				paths.filter(Files::isRegularFile)
					.filter(p -> p.toString().endsWith(".class"))
					.forEach(p -> entries.add(base.relativize(p).toString().replace('\\', '/')));
			}
			Collections.sort(entries);
			for(var name : entries) {
				updateString(digest, name);
				digest.update(Files.readAllBytes(base.resolve(name)));
			}
		}
		else if(Files.isRegularFile(source)) {
			try(var jar = new JarFile(source.toFile())) {
				var jarEntries = jar.entries();
				while(jarEntries.hasMoreElements()) {
					var name = jarEntries.nextElement().getName();
					if(name.startsWith("io/github/pfwikis/") && name.endsWith(".class"))
						entries.add(name);
				}
				Collections.sort(entries);
				for(var name : entries) {
					updateString(digest, name);
					try(InputStream in = jar.getInputStream(jar.getEntry(name))) {
						digest.update(in.readAllBytes());
					}
				}
			}
		}
		else {
			throw new IOException("Unsupported compiler code source "+source);
		}

		return HexFormat.of().formatHex(digest.digest());
	}

	public record CachedContent(String format, String state, List<CachedSlice> slices) {}

	public record CachedSlice(Integer start, Integer end, boolean empty, String blob) {}
}
