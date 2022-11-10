package io.github.pfwikis.layercompiler.steps;

import java.io.File;
import java.io.IOException;

import io.github.pfwikis.Tools;

public class GenerateBorderVariants extends LCStep {

    @Override
    public File process(Ctx ctx, File f) throws IOException {
        //province labels
        var provinces = tmpGeojson();
        Tools.mapshaper(f, provinces,
            "-filter", "province !== null",
            "-filter-fields", "province",
            "-rename-fields", "Name=province"
        );
        createNewLayer(new Ctx("borders_provinces", ctx.getOptions(), ctx.getGeo(), provinces));

        //province borders are only the inner lines within countries
        var provinceBorders = tmpGeojson();
        Tools.mapshaper(f, provinceBorders,
            "-filter", "province !== null",
            "-split", "nation",
            "-innerlines",
            "-merge-layers",
            "-clip", new File(ctx.getGeo(), "continents.geojson"),
            "-dashlines", "dash-length=20km", "gap-length=15km", "scaled"
        );
        createNewLayer(new Ctx("borders_provinces_borders", ctx.getOptions(), ctx.getGeo(), provinceBorders));




        //nation labels
        var nations = tmpGeojson();
        Tools.mapshaper(f, nations,
            "-filter", "nation !== null",
            "-rename-fields", "Name=nation",
            "-dissolve", "Name"
        );
        createNewLayer(new Ctx("borders_nations", ctx.getOptions(), ctx.getGeo(), nations));

        //nation borders are the inner lines plus the outer lines on land
        var innerNationBorders = tmpGeojson();
        Tools.mapshaper(f, innerNationBorders,
            "-filter", "nation !== null",
            "-dissolve", "nation",
            "-innerlines"
        );
        var outerNationBorders = tmpGeojson();
        Tools.mapshaper(f, outerNationBorders,
            "-filter", "nation !== null",
            "-dissolve",
            "-lines", "-filter-fields",
            "-clip", new File(ctx.getGeo(), "continents.geojson")
        );
        var nationBorders = tmpGeojson();
        Tools.mapshaper(innerNationBorders, nationBorders,
            outerNationBorders, "combine-files",
            "-merge-layers"
        );
        createNewLayer(new Ctx("borders_nations_borders", ctx.getOptions(), ctx.getGeo(), nationBorders));




        //region labels
        var regions = tmpGeojson();
        Tools.mapshaper(f, regions,
            "-filter", "region !== null",
            "-rename-fields", "Name=region",
            "-dissolve", "Name"
        );
        createNewLayer(new Ctx("borders_regions", ctx.getOptions(), ctx.getGeo(), regions));

        //region borders are the inner lines
        var regionBorders = tmpGeojson();
        Tools.mapshaper(f, regionBorders,
            "-filter", "region !== null",
            "-dissolve", "region",
            "-innerlines"
        );
        createNewLayer(new Ctx("borders_regions_borders", ctx.getOptions(), ctx.getGeo(), regionBorders));



        return null;
    }

}
