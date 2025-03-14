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
                "-clip", getInput("land_without_water"),
                "-dashlines", "dash-length=20km", "gap-length=15km", "scaled"
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
            var innerNationBorders = Tools.mapshaper(this, getInput(),
                "-filter", "Boolean(nation)",
                "-dissolve2", "nation",
                "-innerlines"
            );
            var outerNationBorders = Tools.mapshaper(this, getInput(),
                "-filter", "Boolean(nation)",
                "-dissolve2",
                "-lines", "-filter-fields",
                "-clip", getInput("land_without_water")
            );
            var res = Tools.mapshaper2(this, innerNationBorders,
                outerNationBorders, "combine-files",
                "-merge-layers"
            );
            innerNationBorders.finishUsage();
            outerNationBorders.finishUsage();
            
            return res;
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
            var innerSubRegionBorders = Tools.mapshaper(this, getInput(),
                "-each", "if(subregion) {nation = subregion;}",
                "-filter", "Boolean(nation)",
                "-dissolve2", "nation",
                "-innerlines"
            );
            var outerSubRegionBorders = Tools.mapshaper(this, getInput(),
                "-each", "if(subregion) {nation = subregion;}",
                "-filter", "Boolean(nation)",
                "-dissolve2",
                "-lines", "-filter-fields",
                "-clip", getInput("land_without_water")
            );
            var res = Tools.mapshaper2(this, innerSubRegionBorders,
                outerSubRegionBorders, "combine-files",
                "-merge-layers"
            );
            innerSubRegionBorders.finishUsage();
            outerSubRegionBorders.finishUsage();
            
            return res;
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
