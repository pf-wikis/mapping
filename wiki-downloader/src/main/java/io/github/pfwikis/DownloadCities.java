package io.github.pfwikis;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.pfwikis.model.*;

public class DownloadCities {

    public static void main(String[] args) throws IOException {
        String url = Helper.buildQuery("https://pathfinderwiki.com/w/api.php",
            "action","ask",
            "format","json",
            "utf8","1",
            "api_version","3","formatversion","2",
            "query", String.join("",
                   "[[Has meta type::City]][[Has coordinates::+]][[:+]]",
                "OR [[Has meta type::City]][[Has coordinates::+]][[-Has subobject::PathfinderWiki:Map Locations Without Articles]]",
                "|?Has population",
                "|?Is capital",
                "|?Is size",
                "|?Has coordinates",
                "|?Has name",
                "|?Has infobox type"
            )
        );
        int offset = 0;

        var cities = new ArrayList<City>();

        while (true) {
            var type = new ObjectMapper().getTypeFactory().constructParametricType(Response.class, City.class);
            Response<City> array = Helper.read(url + URLEncoder.encode("|offset=" + offset, StandardCharsets.UTF_8), type);
            offset += 50;
            cities.addAll(array.getQuery().getResults());
            if (array.getQuery().getResults().size() < 50) {
                break;
            }
        }
        cities.removeIf(c->"City district".equals(c.getIbtype()));
        cities.sort(Comparator.comparing(City::getName));

        System.out.println("Found " + cities.size() + " cities.");
        System.out.println("Downloading texts");
        for (var city : cities) {
        	if(!city.getPageName().startsWith("PathfinderWiki:")) {
        		System.out.println("\t"+city.getPageName());
        		city.setText(Helper.downloadText(city.getPageName()));
        	}
        }

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
        if (StringUtils.isNumeric(city.getPopulation())) {
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
