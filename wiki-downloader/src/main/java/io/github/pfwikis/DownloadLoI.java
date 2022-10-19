package io.github.pfwikis;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

import org.apache.commons.io.IOUtils;

import com.google.gson.GsonBuilder;

import io.github.pfwikis.model.*;
import io.github.pfwikis.model.Properties;

public class DownloadLoI {

    public static void main(String[] args) throws MalformedURLException, IOException {
        String url = "https://pathfinderwiki.com/w/index.php?title=Special:CargoExport"
            + "&tables=LocationOfInterest%2C&"
            + "&fields=LocationOfInterest._pageName%2C+LocationOfInterest.latlong%2C+LocationOfInterest.type"
            + "&where=LocationOfInterest.latlong__full+IS+NOT+NULL+AND+LocationOfInterest._pageNamespace=0"
            + "&order+by=%60mw_cargo__LocationOfInterest%60.%60_pageName%60%2C%60mw_cargo__LocationOfInterest%60.%60latlong__full%60"
            + "&limit=1000&format=json";
        int offset = 0;
        var gson = new GsonBuilder().setPrettyPrinting().create();

        var lois = new ArrayList<LoI>();

        while(true) {
            try (var in = new URL(url+"&offset="+offset).openStream()) {
                String content = IOUtils.toString(in, StandardCharsets.UTF_8);
                var array = gson.fromJson(content, LoI[].class);
                if(array.length == 0) {
                    break;
                }
                offset+=1000;
                lois.addAll(Arrays.asList(array));
            }
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
        var rawResult = gson.toJson(result);
        Files.writeString(new File("../sources/locations.geojson").toPath(), rawResult);
    }

    private static void handleName(LoI city, Properties properties) {
        String name = city.getPageName();
        name = name.replaceAll(" +\\(.*", "");
        properties.setName(name);
    }
}
