package io.github.pfwikis.layercompiler.steps;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

import io.github.pfwikis.layercompiler.cache.CacheManager;
import io.github.pfwikis.layercompiler.description.Ctx;
import io.github.pfwikis.layercompiler.steps.model.Inputs;
import io.github.pfwikis.layercompiler.steps.model.StepExecutor;
import io.github.pfwikis.layercompiler.steps.model.Time;
import io.github.pfwikis.layercompiler.steps.model.content.Content;
import io.github.pfwikis.layercompiler.steps.model.data.GeoData;
import io.github.pfwikis.run.Tools;
import io.github.pfwikis.util.time.TimeRange;
import lombok.Getter;

@Time.Requirement(Time.Requirement.Value.ANY)
public class ReadFile extends StepExecutor {

    private final File file;
    @Getter
    private final String layer;

    
    public ReadFile(File file, String layer) {
		this.file = file;
		this.layer = layer;
		
		if(file == null) {
			Objects.requireNonNull(layer, "Either file or layer must not be null for READ_FILE.");
		}
	}


	@Override
    public Content process(Inputs in) throws IOException {
		GeoData res;
		if(file != null) {
			res = GeoData.from(resolveFile());
		}
		else {
			res = Tools.mapshaper(
				this,
				GeoData.from(Ctx.INSTANCE.getOptions().getMappingDataFile()),
				"layers="+layer,
				"-filter-fields", "fid", "invert"
			);
        }
		
		boolean hasTime = res.toFeatureCollection().getFeatures()
			.stream()
			.anyMatch(f->f.getProperties().getTime() != null && !f.getProperties().getTime().equals(TimeRange.always()));
		
		return hasTime?Content.merged(res):Content.timeless(res);
    }

	@Override
	public byte[] getCacheSalt() {
		var source = file != null
			? resolveFile()
			: Ctx.INSTANCE.getOptions().getMappingDataFile();
		return CacheManager.INSTANCE.hashFile(source.toPath());
	}

	private File resolveFile() {
		return Path.of("../sources").resolve(file.toPath()).toAbsolutePath().normalize().toFile();
	}

}
