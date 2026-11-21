package io.github.pfwikis.layercompiler.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.pfwikis.CLIOptions;
import io.github.pfwikis.layercompiler.description.Ctx;
import io.github.pfwikis.layercompiler.description.StepDescription;
import io.github.pfwikis.layercompiler.steps.model.Inputs;
import io.github.pfwikis.layercompiler.steps.model.StepExecutor;
import io.github.pfwikis.layercompiler.steps.model.content.Content;
import io.github.pfwikis.layercompiler.steps.model.data.GeoData;
import io.github.pfwikis.model.FeatureCollection;

class CacheManagerTest {

	@Test
	void storesLoadsAndSweepsUnusedFiles(@TempDir Path directory) throws Exception {
		Ctx.INSTANCE.setOptions(new CLIOptions());
		CacheManager.INSTANCE.initialize(directory.toFile(), false);
		try {
			var step = new TestStep();
			var description = new StepDescription("test.GROUP", "test", "GROUP", step);
			step.setDescription(description);

			var key = CacheManager.INSTANCE.keyFor(step);
			assertThat(key).isNotBlank();

			var content = Content.timeless(GeoData.from(new FeatureCollection()));
			CacheManager.INSTANCE.store(key, content);

			var entry = directory.resolve("entries").resolve(key+".json");
			assertThat(entry).exists();

			assertThat(CacheManager.INSTANCE.load(key)).isNotNull();

			var unused = directory.resolve("entries").resolve("unused.json");
			Files.writeString(unused, "{}");
			CacheManager.INSTANCE.sweep();

			assertThat(unused).doesNotExist();
			assertThat(entry).exists();
		} finally {
			CacheManager.INSTANCE.initialize(null, true);
		}
	}

	private static class TestStep extends StepExecutor {
		@Override
		protected Content process(Inputs in) {
			return Content.empty();
		}
	}
}
