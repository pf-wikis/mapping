package io.github.pfwikis.layercompiler.steps;

import java.io.File;
import java.io.IOException;

import io.github.pfwikis.run.Tools;

public class GenerateBorderVariants extends LCStep {

    @Override
    public byte[] process(Ctx ctx, byte[] f) throws IOException {
        //province labels
        var provinces = Tools.mapshaper(f,
            "-filter", "province !== null",
            "-filter-fields", "province",
            "-rename-fields", "Name=province"
        );
        createNewLayer(new Ctx("borders_provinces", ctx.getOptions(), ctx.getGeo(), provinces));

        //province borders are only the inner lines within countries
        var provinceBorders = Tools.mapshaper(f,
            "-filter", "province !== null",
            "-split", "nation",
            "-innerlines",
            "-merge-layers",
            "-clip", new File(ctx.getGeo(), "continents.geojson"),
            "-erase", new File(ctx.getGeo(), "waters.geojson"),
            "-dashlines", "dash-length=20km", "gap-length=15km", "scaled"
        );
        createNewLayer(new Ctx("borders_provinces_borders", ctx.getOptions(), ctx.getGeo(), provinceBorders));




        //nation labels
        var nations = Tools.mapshaper(f,
            "-filter", "nation !== null",
            "-rename-fields", "Name=nation",
            "-dissolve", "Name"
        );
        createNewLayer(new Ctx("borders_nations", ctx.getOptions(), ctx.getGeo(), nations));

        //nation borders are the inner lines plus the outer lines on land
        var innerNationBorders = Tools.mapshaper(f,
            "-filter", "nation !== null",
            "-dissolve", "nation",
            "-innerlines"
        );
        var outerNationBorders = Tools.mapshaper(f,
            "-filter", "nation !== null",
            "-dissolve",
            "-lines", "-filter-fields",
            "-clip", new File(ctx.getGeo(), "continents.geojson"),
            "-erase", new File(ctx.getGeo(), "waters.geojson")
        );
        var nationBorders = Tools.mapshaper2(innerNationBorders,
            outerNationBorders, "combine-files",
            "-merge-layers"
        );
        createNewLayer(new Ctx("borders_nations_borders", ctx.getOptions(), ctx.getGeo(), nationBorders));



        //subregion labels
        var subregions = Tools.mapshaper(f,
            "-filter", "region !== null",
            "-rename-fields", "Name=subregion",
            "-dissolve", "Name"
        );
        createNewLayer(new Ctx("borders_subregions", ctx.getOptions(), ctx.getGeo(), subregions));

        //subregion borders are the inner lines
        var subregionBorders = Tools.mapshaper(f,
            "-filter", "subregion !== null",
            "-dissolve", "subregion",
            "-innerlines"
        );
        createNewLayer(new Ctx("borders_subregions_borders", ctx.getOptions(), ctx.getGeo(), subregionBorders));


        //region labels
        var regions = Tools.mapshaper(f,
            "-filter", "region !== null",
            "-rename-fields", "Name=region",
            "-dissolve", "Name"
        );
        createNewLayer(new Ctx("borders_regions", ctx.getOptions(), ctx.getGeo(), regions));

        //region borders are the inner lines
        var regionBorders = Tools.mapshaper(f,
            "-filter", "region !== null",
            "-dissolve", "region",
            "-innerlines"
        );
        createNewLayer(new Ctx("borders_regions_borders", ctx.getOptions(), ctx.getGeo(), regionBorders));

        return null;
    }

}
