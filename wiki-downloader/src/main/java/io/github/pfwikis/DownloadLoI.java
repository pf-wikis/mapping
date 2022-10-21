package io.github.pfwikis;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import io.github.pfwikis.model.*;

public class DownloadLoI {

    public static void main(String[] args) throws MalformedURLException, IOException {
        String url = "https://pathfinderwiki.com/w/index.php?title=Special:CargoExport"
            + "&tables=LocationOfInterest%2C&"
            + "&fields=LocationOfInterest._pageName%2C+LocationOfInterest.latlong%2C+LocationOfInterest.type"
            + "&where=LocationOfInterest.latlong__full+IS+NOT+NULL+AND+LocationOfInterest._pageNamespace=0"
            + "&order+by=%60mw_cargo__LocationOfInterest%60.%60_pageName%60%2C%60mw_cargo__LocationOfInterest%60.%60latlong__full%60"
            + "&limit=1000&format=json";
        int offset = 0;
        var jackson = Jackson.get();

        var lois = new ArrayList<LoI>();

        while(true) {
            var array = jackson.readValue(new URL(url+"&offset="+offset), LoI[].class);
            if(array.length == 0) {
                break;
            }
            offset+=1000;
            lois.addAll(Arrays.asList(array));
        }
        lois.sort(Comparator.comparing(LoI::getPageName));


        System.out.println("Found "+lois.size()+" LoIs.");

        var arr = new ArrayList<Feature>();
        for (var loi : lois) {
            try {
                var feature = new Feature();
                var properties = new Properties();
                feature.setProperties(properties);
                handleName(loi, properties);
                properties.setLink("https://pathfinderwiki.com/wiki/"+loi.getPageName().replace(' ', '_'));
                feature.getTippecanoe().setMinzoom(5);
                properties.setType(loi.getType());
                var geometry = new Geometry();
                feature.setGeometry(geometry);
                geometry.setCoordinates(List.of(
                    loi.getCoordsLon(),
                    loi.getCoordsLat()));
                arr.add(feature);
            } catch(Exception e) {
                System.err.println("Failed for "+loi.getPageName());
                e.printStackTrace();
            }
        }

        var result = new FeatureCollection();
        result.setName("cities");
        result.setFeatures(arr);
        jackson.writer().withDefaultPrettyPrinter().writeValue(new File("../sources/locations.geojson"), result);
    }

    private static void handleName(LoI city, Properties properties) {
        String name = city.getPageName();
        name = name.replaceAll(" +\\(.*", "");
        properties.setName(name);
    }
}
