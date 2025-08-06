package io.github.pfwikis.layercompiler.steps;

import io.github.pfwikis.layercompiler.steps.model.LCContent;
import io.github.pfwikis.layercompiler.steps.model.LCStep;
import io.github.pfwikis.run.Tools;

public class BorderVariants {

    public static class Provinces extends LCStep {
        @Override
        public LCContent process() throws Exception {
            return Tools.mapshaper(this, getInput(),
                "-filter", "Boolean(province)",
                "-filter-fields", "province",
                "-rename-fields", "label=province"
            );
        }
    }

    public static class ProvinceBorders extends LCStep {
        @Override
        public LCContent process() throws Exception {
            return Tools.mapshaper(this, getInput(),
                "-filter", "Boolean(province)",
                "-split", "nation",
                "-innerlines",
                "-merge-layers",
                "-clip", getInput("land_without_water")
            );
        }
    }

    public static class Nations extends LCStep {
        @Override
        public LCContent process() throws Exception {
            return Tools.mapshaper(this, getInput(),
                "-filter", "Boolean(nation)",
                "-each", "inSubregion=Boolean(subregion)",
                "-rename-fields", "label=nation",
                "-dissolve2", "label", "copy-fields=inSubregion"
            );
        }
    }

    public static class NationBorders extends LCStep {
        @Override
        public LCContent process() throws Exception {
        	return Tools.mapshaper(this, getInput(),
        		"-each", "key=nation||subregion", //because subregions without nations are probably empires
        		"-filter", "Boolean(key)", //filter out gap fillers
        		"-dissolve2", "key",
        		"-lines",
        		"-split", "TYPE", //splits into inner and outer
        		"-clip", getInput("land_without_water"), "target=outer",
        		"-merge-layers", "target=inner,outer",
        		"-filter-fields",
        		"-explode"
        	);
        }
    }

    public static class Subregions extends LCStep {
        @Override
        public LCContent process() throws Exception {
            return Tools.mapshaper(this, getInput(),
                "-filter", "Boolean(subregion)",
                "-rename-fields", "label=subregion",
                "-dissolve2", "label"
            );
        }
    }

    public static class SubregionBorders extends LCStep {
        @Override
        public LCContent process() throws Exception {
            //subregion borders are like nation border but with subregion overwriting the nations
        	return Tools.mapshaper(this, getInput(),
        		"-each", "key=subregion||nation",
        		"-filter", "Boolean(key)", //filter out gap fillers
        		"-dissolve2", "key",
        		"-lines",
        		"-split", "TYPE", //splits into inner and outer
        		"-clip", getInput("land_without_water"), "target=outer",
        		"-merge-layers", "target=inner,outer",
        		"-filter-fields",
        		"-explode"
        	);
        }
    }

    public static class Regions extends LCStep {
        @Override
        public LCContent process() throws Exception {
            var regions = Tools.mapshaper(this, getInput(),
                "-filter", "Boolean(region)",
                "-rename-fields", "label=region",
                "-dissolve2", "label"
            );
            return regions;
        }
    }

    public static class RegionBorders extends LCStep {
        @Override
        public LCContent process() throws Exception {
            return Tools.mapshaper(this, getInput(),
                "-filter", "Boolean(region)",
                "-dissolve2", "region",
                "-innerlines"
            );
        }
    }
}
