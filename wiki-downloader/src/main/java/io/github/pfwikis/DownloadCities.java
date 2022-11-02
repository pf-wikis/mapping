package io.github.pfwikis;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import io.github.pfwikis.model.*;

public class DownloadCities {

    public static void main(String[] args) throws IOException {
        String url = "https://pathfinderwiki.com/w/index.php?title=Special:CargoExport"
            + "&tables=City%2C&"
            + "&fields=City._pageName%2C+City.name%2C+City.population%2C+City.latlong%2C+City.capital%2C+City.size"
            + "&where=City.latlong__full+IS+NOT+NULL+AND+City._pageNamespace=0"
            + "&order+by=%60mw_cargo__City%60.%60_pageName%60%2C%60mw_cargo__City%60.%60latlong__full%60"
            + "&limit=1000&format=json";
        int offset = 0;
        var jackson = Jackson.get();

        var cities = new ArrayList<City>();

        while (true) {
            City[] array = jackson.readValue(new URL(url + "&offset=" + offset), City[].class);
            if (array.length == 0) {
                break;
            }
            offset += 1000;
            cities.addAll(Arrays.asList(array));
        }
        cities.sort(Comparator.comparing(City::getPageName));

        System.out.println("Found " + cities.size() + " cities.");

        var arr = new ArrayList<Feature>();
        for (var city : cities) {
            try {
                var feature = new Feature();
                var properties = new Properties();
                handleName(city, properties);
                properties.setLink("https://pathfinderwiki.com/wiki/" + city.getPageName().replace(' ', '_'));
                properties.setCapital(city.getCapital() == 1);
                feature.setProperties(properties);
                handlePopulation(city, feature);

                var geometry = new Geometry();
                geometry.setCoordinates(List.of(city.getCoordsLon(), city.getCoordsLat()));
                feature.setGeometry(geometry);
                arr.add(feature);
            } catch (Exception e) {
                System.err.println("Failed for " + city.getPageName());
                e.printStackTrace();
            }
        }

        var result = new FeatureCollection("cities", arr);
        jackson.writer().withDefaultPrettyPrinter().writeValue(new File("../sources/cities.geojson"), result);
    }

    private static void handleName(City city, Properties properties) {
        String name = city.getPageName();
        name = name.replaceAll(" +\\(.*", "");
        properties.setName(name);
    }

    private static void handlePopulation(City city, Feature feature) {
        int size = mapSize(city);

        feature.getProperties().setSize(size);
        feature.getTippecanoe().setMinzoom(switch (size) {
            case 0 -> 2;
            case 1 -> 4;
            case 2 -> 5;
            default -> 6;
        });
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
            return 3;
        } else if (city.getSize() != null && !city.getSize().isEmpty()) {
            String size = city.getSize();
            if (StringUtils.containsAnyIgnoreCase(size, "Category:Thorps", "Category:Hamlets", "Category:Villages")) {
                return 3;
            } else if (StringUtils.containsAnyIgnoreCase(size, "Category:Small_towns", "Category:Large_towns", "Town")) {
                return 2;
            } else if (StringUtils.containsAnyIgnoreCase(size, "City", "Category:Small_cities", "Category:Large_cities")) {
                return 1;
            } else if (StringUtils.containsAnyIgnoreCase(size, "Category:Metropolises", "Metropolis")) {
                return 0;
            } else if (StringUtils.containsAnyIgnoreCase(size, "Abandoned", "Ruins")) {
                return 3; //TODO maybe these should be locations instead?
            }
            return 3;
        }
        return 3;
    }
}
