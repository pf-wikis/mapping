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

public class DownloadCities {

    public static void main(String[] args) throws MalformedURLException, IOException {
        String url = "https://pathfinderwiki.com/w/index.php?title=Special:CargoExport"
            + "&tables=City%2C&"
            + "&fields=City._pageName%2C+City.name%2C+City.population%2C+City.latlong%2C+City.capital"
            + "&where=City.latlong__full+IS+NOT+NULL+AND+City._pageNamespace=0"
            + "&order+by=%60mw_cargo__City%60.%60_pageName%60%2C%60mw_cargo__City%60.%60latlong__full%60"
            + "&limit=1000&format=json";
        int offset = 0;
        var gson = new GsonBuilder().setPrettyPrinting().create();

        var cities = new ArrayList<City>();

        while(true) {
            try (var in = new URL(url+"&offset="+offset).openStream()) {
                String content = IOUtils.toString(in, StandardCharsets.UTF_8);
                var array = gson.fromJson(content, City[].class);
                if(array.length == 0) {
                    break;
                }
                offset+=1000;
                cities.addAll(Arrays.asList(array));
            }
        }
        cities.sort(Comparator.comparing(City::getPageName));


        System.out.println("Found "+cities.size()+" cities.");

        var arr = new ArrayList<Feature>();
        for (var city : cities) {
            try {
                var feature = new Feature();
                var properties = new Properties();
                feature.setProperties(properties);
                handleName(city, properties);
                properties.setLink("https://pathfinderwiki.com/wiki/"+city.getPageName().replace(' ', '_'));
                properties.setCapital(city.getCapital() == 1);
                handlePopulation(city, feature);
                var geometry = new Geometry();
                feature.setGeometry(geometry);
                geometry.setCoordinates(List.of(
                    city.getCoordsLon(),
                    city.getCoordsLat()));
                arr.add(feature);
            } catch(Exception e) {
                System.err.println("Failed for "+city.getPageName());
                e.printStackTrace();
            }
        }

        var result = new FeatureCollection();
        result.setName("cities");
        result.setFeatures(arr);
        var rawResult = gson.toJson(result);
        Files.writeString(new File("../sources/cities.geojson").toPath(), rawResult);
    }

    private static void handleName(City city, Properties properties) {
        String name = city.getPageName();
        name = name.replaceAll(" +\\(.*", "");
        properties.setName(name);
    }

    private static void handlePopulation(City city, Feature feature) {
        long population;
        if(city.getPopulation() != null && !city.getPopulation().isEmpty()) {
            population = Long.parseLong(city.getPopulation());
        }
        else {
            population = 1000;
        }

        if(population > 100000) {
            feature.getProperties().setSize(0);
            feature.getTippecanoe().setMinzoom(2);
        }
        else if(population > 10000) {
            feature.getProperties().setSize(1);
            feature.getTippecanoe().setMinzoom(4);
        }
        else if(population > 1000) {
            feature.getProperties().setSize(2);
            feature.getTippecanoe().setMinzoom(5);
        }
        else {
            feature.getProperties().setSize(3);
            feature.getTippecanoe().setMinzoom(6);
        }


    }
}
