package io.github.pfwikis.layercompiler.steps;

import java.io.IOException;

import io.github.pfwikis.layercompiler.steps.model.Inputs;
import io.github.pfwikis.layercompiler.steps.model.StepExecutor;
import io.github.pfwikis.layercompiler.steps.model.Time;
import io.github.pfwikis.layercompiler.steps.model.content.Content;
import io.github.pfwikis.run.Tools;

public class BorderVariants {

	@Time.Requirement(Time.Requirement.Value.ANY)
    public static class Provinces extends StepExecutor {
		@Override
		public Content process(Inputs in) throws IOException {
			return Content.derivedFrom(in, Tools.mapshaper(this, in.getInput(),
                "-filter", "Boolean(province)",
                "-filter-fields", "province"+in.getTimeState().mapshaperTimeFields(),
                "-rename-fields", "label=province"
            ));
		}
    }

	@Time.Requirement(Time.Requirement.Value.REQUIRES_SLICED)
    public static class ProvinceBorders extends StepExecutor {
		@Override
		public Content process(Inputs in) throws Exception {
			return Content.timeless(Tools.mapshaper(this, in.getInput(),
	                "-filter", "Boolean(province)",
	                "-split", "nation",
	                "-innerlines",
	                "-merge-layers",
	                "-clip", in.getInput("land_without_water")
	            ));
		}
    }
    
	@Time.Requirement(Time.Requirement.Value.REQUIRES_SLICED)
    public static class DistrictBorders extends StepExecutor {
		@Override
		public Content process(Inputs in) throws Exception {
			return Content.timeless(Tools.mapshaper(this, in.getInput(),
	                "-lines",
	                "-dissolve"
	            ));
		}
    }

	@Time.Requirement(Time.Requirement.Value.REQUIRES_SLICED)
    public static class Nations extends StepExecutor {
		@Override
		public Content process(Inputs in) throws Exception {
			return Content.timeless(Tools.mapshaper(this, in.getInput(),
                "-filter", "Boolean(nation)",
                "-each", "inSubregion=Boolean(subregion)",
                "-rename-fields", "label=nation",
                "-dissolve", "label", "copy-fields=inSubregion"
            ));
		}
    }

	@Time.Requirement(Time.Requirement.Value.REQUIRES_SLICED)
    public static class NationBorders extends StepExecutor {
		@Override
		public Content process(Inputs in) throws Exception {
			return Content.timeless(Tools.mapshaper(this, in.getInput(),
        		"-each", "key=nation||subregion", //because subregions without nations are probably empires
        		"-filter", "Boolean(key)", //filter out gap fillers
        		"-dissolve", "key",
        		"-lines",
        		"-split", "TYPE", //splits into inner and outer
        		"-clip", in.getInput("land_without_water"), "target=outer",
        		"-merge-layers", "target=inner,outer",
        		"-filter-fields",
        		"-explode"
        	));
		}
    }

	@Time.Requirement(Time.Requirement.Value.REQUIRES_SLICED)
    public static class Subregions extends StepExecutor {
		@Override
		public Content process(Inputs in) throws Exception {
			return Content.timeless(Tools.mapshaper(this, in.getInput(),
                "-filter", "Boolean(subregion)",
                "-rename-fields", "label=subregion",
                "-dissolve", "label"
            ));
		}
    }

	@Time.Requirement(Time.Requirement.Value.REQUIRES_SLICED)
    public static class SubregionBorders extends StepExecutor {
		@Override
		public Content process(Inputs in) throws Exception {
			//subregion borders are like nation border but with subregion overwriting the nations
        	return Content.timeless(Tools.mapshaper(this, in.getInput(),
        		"-each", "key=subregion||nation",
        		"-filter", "Boolean(key)", //filter out gap fillers
        		"-dissolve", "key",
        		"-lines",
        		"-split", "TYPE", //splits into inner and outer
        		"-clip", in.getInput("land_without_water"), "target=outer",
        		"-merge-layers", "target=inner,outer",
        		"-filter-fields",
        		"-explode"
        	));
		}
    }

	@Time.Requirement(Time.Requirement.Value.REQUIRES_SLICED)
    public static class Regions extends StepExecutor {
		@Override
		public Content process(Inputs in) throws Exception {
			var regions = Tools.mapshaper(this, in.getInput(),
                "-filter", "Boolean(region)",
                "-rename-fields", "label=region",
                "-dissolve", "label"
            );
            return Content.timeless(regions);
		}
    }

	@Time.Requirement(Time.Requirement.Value.REQUIRES_SLICED)
    public static class RegionBorders extends StepExecutor {
		@Override
		public Content process(Inputs in) throws Exception {
			return Content.timeless(Tools.mapshaper(this, in.getInput(),
                "-filter", "Boolean(region)",
                "-dissolve", "region",
                "-innerlines"
            ));
		}
    }
}
