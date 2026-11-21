package io.github.pfwikis.build;

import java.nio.file.Files;
import java.nio.file.Path;

import io.github.pfwikis.layercompiler.cache.CacheManager;

/**
 * Runs during Maven's process-classes phase and stores the compiler class hash as a resource.
 * At runtime {@link CacheManager} can reuse it instead of hashing every class file again.
 */
public final class BuildCacheHash {

	private BuildCacheHash() {}

	public static void main(String[] args) throws Exception {
		var outputDirectory = Path.of(args.length > 0 ? args[0] : "target/classes");
		var hash = CacheManager.computeClassHash(outputDirectory);
		var target = outputDirectory.resolve(CacheManager.BUILD_CLASS_HASH_PATH);
		Files.createDirectories(target.getParent());
		Files.writeString(target, hash+"\n");
		System.out.println("Compiler cache class hash: "+hash);
	}
}
