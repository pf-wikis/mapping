package io.github.pfwikis.layercompiler.steps;

import java.io.File;

import com.google.common.io.Files;

import io.github.pfwikis.run.Tools;

public class BorderVariants {

    public static class Provinces extends LCStep {
        @Override
        public byte[] process() throws Exception {
            return Tools.mapshaper(getInput(),
                "-filter", "province !== null",
                "-filter-fields", "province",
                "-rename-fields", "Name=province"
            );
        }
    }

    public static class ProvinceBorders extends LCStep {
        @Override
        public byte[] process() throws Exception {
            return Tools.mapshaper(getInput(),
                "-filter", "province !== null",
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
        public byte[] process() throws Exception {
            return Tools.mapshaper(getInput(),
                "-filter", "nation !== null",
                "-each", "inSubregion=(subregion!==null)",
                "-rename-fields", "Name=nation",
                "-dissolve2", "Name", "copy-fields=inSubregion"
            );
        }
    }

    public static class NationBorders extends LCStep {
        @Override
        public byte[] process() throws Exception {
            var innerNationBorders = Tools.mapshaper(getInput(),
                "-filter", "nation !== null",
                "-dissolve2", "nation",
                "-innerlines"
            );
            var outerNationBorders = Tools.mapshaper(getInput(),
                "-filter", "nation !== null",
                "-dissolve2",
                "-lines", "-filter-fields",
                "-clip", getInput("land_without_water")
            );
            return Tools.mapshaper2(innerNationBorders,
                outerNationBorders, "combine-files",
                "-merge-layers"
            );
        }
    }

    public static class Subregions extends LCStep {
        @Override
        public byte[] process() throws Exception {
            return Tools.mapshaper(getInput(),
                "-filter", "subregion !== null",
                "-rename-fields", "Name=subregion",
                "-dissolve2", "Name"
            );
        }
    }

    public static class SubregionBorders extends LCStep {
        @Override
        public byte[] process() throws Exception {
            //subregion borders are like nation border but with subregion overwriting the nations
            var innerSubRegionBorders = Tools.mapshaper(getInput(),
                "-each", "if(subregion !== null) {nation = subregion;}",
                "-filter", "nation !== null",
                "-dissolve2", "nation",
                "-innerlines"
            );
            var outerSubRegionBorders = Tools.mapshaper(getInput(),
                "-each", "if(subregion !== null) {nation = subregion;}",
                "-filter", "nation !== null",
                "-dissolve2",
                "-lines", "-filter-fields",
                "-clip", getInput("land_without_water")
            );
            return Tools.mapshaper2(innerSubRegionBorders,
                outerSubRegionBorders, "combine-files",
                "-merge-layers"
            );
        }
    }

    public static class Regions extends LCStep {
        @Override
        public byte[] process() throws Exception {
            var regions = Tools.mapshaper(getInput(),
                "-filter", "region !== null",
                "-rename-fields", "Name=region",
                "-dissolve2", "Name"
            );
            return regions;
        }
    }

    public static class RegionBorders extends LCStep {
        @Override
        public byte[] process() throws Exception {
            return Tools.mapshaper(getInput(),
                "-filter", "region !== null",
                "-dissolve2", "region",
                "-innerlines"
            );
        }
    }
}
