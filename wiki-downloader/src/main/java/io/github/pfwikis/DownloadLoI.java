package io.github.pfwikis;

import static io.github.pfwikis.model.Geometry.ROUND_TO_7;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import io.github.pfwikis.model.*;

public class DownloadLoI {

    public static void main(String[] args) throws IOException {
        String url = Helper.buildQuery("https://pathfinderwiki.com/w/index.php",
            "title","Special:CargoExport",
            "tables","LocationOfInterest",
            "fields","_pageName,latlong,type,name",
            "where","latlong__full IS NOT NULL AND (_pageNamespace=0 OR _pageName='PathfinderWiki:Map Locations Without Articles')",
            "order by", "`_pageName`,`name`,`latlong__full`",
            "limit","1000",
            "format","json","parse values","yes"
        );
        int offset = 0;

        var lois = new ArrayList<LoI>();

        while (true) {
            LoI[] array = Helper.read(url + "&offset=" + offset, LoI[].class);
            offset += 1000;
            lois.addAll(Arrays.asList(array));
            if (array.length < 1000) {
                break;
            }
        }
        lois.sort(Comparator.comparing(LoI::getPageName));

        System.out.println("Found " + lois.size() + " LoIs.");

        var arr = new ArrayList<Feature>();
        for (var loi : lois) {
            try {
                var properties = new Properties();
                properties.setName(Helper.handleName(loi.getName(), loi.getPageName()));
                properties.setLink("https://pathfinderwiki.com/wiki/" + loi.getPageName().replace(' ', '_'));
                properties.setType(loi.getType());
                var geometry = new Geometry();
                geometry.setCoordinates(List.of(
                    loi.getCoordsLon().round(ROUND_TO_7),
                    loi.getCoordsLat().round(ROUND_TO_7)
                ));

                var feature = new Feature(properties, geometry);

                arr.add(feature);
            } catch (Exception e) {
                System.err.println("Failed for " + loi.getPageName());
                e.printStackTrace();
            }
        }

        var result = new FeatureCollection("cities", arr);
        Jackson.get().writer().withDefaultPrettyPrinter().writeValue(new File("../sources/locations.geojson"), result);
    }
}
