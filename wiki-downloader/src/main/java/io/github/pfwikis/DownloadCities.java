package io.github.pfwikis;

import java.io.File;
import java.io.IOException;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import io.github.pfwikis.model.*;

public class DownloadCities {

    public static void main(String[] args) throws IOException {
        String url = Helper.buildQuery("https://pathfinderwiki.com/w/index.php",
            "title","Special:CargoExport",
            "tables","City",
            "fields","_pageName,latlong,population,size,name,capital",
            "where","latlong__full IS NOT NULL AND (_pageNamespace=0 OR _pageName='PathfinderWiki:Map Locations Without Articles')",
            "order by", "`_pageName`,`name`,`latlong__full`",
            "limit","1000",
            "format","json","parse values","yes"
        );
        int offset = 0;

        var cities = new ArrayList<City>();

        while (true) {
            City[] array = Helper.read(url + "&offset=" + offset, City[].class);
            offset += 1000;
            cities.addAll(Arrays.asList(array));
            if (array.length < 1000) {
                break;
            }
        }
        cities.sort(Comparator.comparing(City::getPageName));

        System.out.println("Found " + cities.size() + " cities.");

        var arr = new ArrayList<Feature>();
        for (var city : cities) {
            try {
                var feature = new Feature();
                feature.setProperties(new Properties(city));
                handlePopulation(city, feature);

                feature.setGeometry(new Geometry(city));
                arr.add(feature);
            } catch (Exception e) {
                System.err.println("Failed for " + city.getPageName());
                e.printStackTrace();
            }
        }

        var result = new FeatureCollection("cities", arr);
        Jackson.get().writer().withDefaultPrettyPrinter().writeValue(new File("../sources/cities.geojson"), result);
    }

    private static void handlePopulation(City city, Feature feature) {
        int size = mapSize(city);

        feature.getProperties().setSize(size);
    }

    private static int mapSize(City city) {
        if (city.getPopulation() != null && !city.getPopulation().isEmpty()) {
            long population = Long.parseLong(city.getPopulation());
            if (population > 25000) {
                return 0;
            } else if (population > 5000) {
                return 1;
            } else if (population > 201) {
                return 2;
            }
        } else if (city.getSize() != null && !city.getSize().isEmpty()) {
            String size = city.getSize();
            if (StringUtils.containsAnyIgnoreCase(size, "Thorp", "Hamlet", "Village")) {
                return 3;
            } else if (StringUtils.containsIgnoreCase(size, "Town")) {
                return 2;
            } else if (StringUtils.containsAnyIgnoreCase(size, "City", "cities")) {
                return 1;
            } else if (StringUtils.containsIgnoreCase(size, "Metropolis")) {
                return 0;
            } else if (StringUtils.containsAnyIgnoreCase(size, "Abandoned", "Ruins")) {
                return 3; //TODO maybe these should be locations instead?
            }
        }
        return 3;
    }
}
