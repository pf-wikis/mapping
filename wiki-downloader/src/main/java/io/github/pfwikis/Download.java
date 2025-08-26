package io.github.pfwikis;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.pfwikis.model.Location;
import io.github.pfwikis.model.Feature;
import io.github.pfwikis.model.FeatureCollection;
import io.github.pfwikis.model.Geometry;
import io.github.pfwikis.model.Properties;
import io.github.pfwikis.model.Response;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class Download {
	public static void main(String... args) throws Exception {
		new Download(
				false,
				Helper.buildQuery("https://pathfinderwiki.com/w/api.php",
			            "action","ask",
			            "format","json",
			            "utf8","1",
			            "api_version","3","formatversion","2",
			            "query", String.join("",
			                   "[[Has meta type::Location of Interest]][[Has coordinates::+]][[:+]]",
			                "OR [[Has meta type::Location of Interest]][[Has coordinates::+]][[-Has subobject::PathfinderWiki:Map Locations Without Articles]]",
			                "|?Has location type",
			                "|?Has coordinates",
			                "|?Has name",
			                "|?-Has subobject.Modification date=Modification date"
			            )
			        )
		).download();
		new Download(
				true,
				Helper.buildQuery("https://pathfinderwiki.com/w/api.php",
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
			                "|?Has infobox type",
			                "|?-Has subobject.Modification date=Modification date"
			            )
			        )
		).download();
	}

	private final boolean cityCalculations;
	private final String query;

	public void download() throws MalformedURLException, IOException {
		var file = new File("../sources/"+(cityCalculations?"cities":"locations")+".geojson");
		var oldFeatures = Jackson.get().readValue(file, FeatureCollection.class);
		
		var locations = new ArrayList<Location>();
		var jType = new ObjectMapper().getTypeFactory().constructParametricType(Response.class, Location.class);
		
		for(int offset=0;;offset+=50) {
			
			Response<Location> array = Helper.read(
					query + URLEncoder.encode("|offset=" + offset, StandardCharsets.UTF_8),
					jType
			);
			locations.addAll(array.getQuery().getResults());
			if (array.getQuery().getResults().size() < 50) {
				break;
			}
		}
		locations.removeIf(c -> "City district".equals(c.getIbtype()));
		locations.sort(Comparator.comparing(Location::getName));

		System.out.println("Found " + locations.size() + " locations.");
		var arr = new ArrayList<Feature>();
		for (var loc : locations) {
			try {
				System.out.println("\t" + loc.getPageName());
				var feature = new Feature();
				arr.add(feature);
				var properties = new Properties(loc);
				feature.setProperties(properties);
				feature.setGeometry(new Geometry(loc.getCoordsLon(), loc.getCoordsLat()));
				
				
				if(cityCalculations)
					properties.setSize(mapPopulationSize(loc));
				
				if (!loc.getPageName().startsWith("PathfinderWiki:")) {
					downloadExcerpt(loc, feature, oldFeatures);
				}
				
			} catch (Exception e) {
				System.err.println("Failed for " + loc.getPageName());
				e.printStackTrace();
			}
		}

		var result = new FeatureCollection("cities", arr);
		Jackson.get().writer().withDefaultPrettyPrinter().writeValue(file, result);

	}

    private void downloadExcerpt(Location city, Feature feature, FeatureCollection oldFeatures) throws IOException {
    	var oldMatches = oldFeatures.getFeatures().stream()
    		.filter(old->feature.getProperties().getLabels().equals(old.getProperties().getLabels()))
    		.filter(old->feature.getGeometry().equals(old.getGeometry()))
    		.toList();  		
    	
    	for(var old:oldMatches) {
    		if(old.getProperties().getModificationDate().equals(feature.getProperties().getModificationDate())
				&& old.getProperties().getText() != null
				&& old.getProperties().getText().length()>0) {
    			System.out.println("\t\tCopying old excerpt based on modification date");
    			feature.getProperties().setText(old.getProperties().getText());
    			feature.getProperties().setArticleLength(old.getProperties().getArticleLength());
    			return;
    		}
    	}
    	
    	System.out.println("\t\tDownloading text");
    	var txt = Helper.downloadText(city.getPageName());
		feature.getProperties().setText(txt.excerpt());
		feature.getProperties().setArticleLength((int)Math.ceil(txt.totalArticleLength()/100.0)*100);
	}

	private static int mapPopulationSize(Location city) {
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
