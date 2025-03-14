package io.github.pfwikis.layercompiler.steps;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import io.github.pfwikis.layercompiler.steps.model.LCContent;
import io.github.pfwikis.layercompiler.steps.model.LCStep;
import io.github.pfwikis.model.Feature;
import io.github.pfwikis.model.Feature.Tippecanoe;
import io.github.pfwikis.model.FeatureCollection;
import io.github.pfwikis.model.Geometry.Point;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LocationGroup extends LCStep {

	@Override
	public LCContent process() throws IOException {
		var col = this.getInput().toFeatureCollection();

		var entries = collectEntries(col);
		double groupingDistance = 1;
		List<Group> allGroups = new ArrayList<>();
		for(int zoom=0;zoom<=ctx.getOptions().getMaxZoom();zoom++,groupingDistance/=2) {
			List<Group> zoomGroups = new ArrayList<>();
			for(var e:entries) {
				addEntry(zoomGroups, e, groupingDistance, zoom);
			}
			//merge or add
			for(var g:zoomGroups) {
				var match = allGroups.stream().filter(p->
					p.best.equals(g.best)
					&& p.maxZoom+1 == g.minZoom
					&& p.entries.equals(g.entries)
				).findAny();
				
				if(match.isPresent()) {
					match.get().maxZoom=g.maxZoom;
				}
				else
					allGroups.add(g);
			}
		}
		
		//create output
		var result = new FeatureCollection();
		result.setFeatures(new ArrayList<>());
		for(var g:allGroups) {
			if(g.minZoom > ctx.getOptions().getMaxZoom()) continue;
			var minMin = g.entries.stream().filter(e->e.feature.getProperties().getFilterMinzoom()!=null).mapToInt(e->e.feature.getProperties().getFilterMinzoom()).min();
			var realMin = Math.max(minMin.orElse(g.minZoom), g.minZoom);
			if(realMin > g.maxZoom) continue;
			
			Feature f = g.best.feature.copy();
			f.setTippecanoe(new Tippecanoe());
			f.getTippecanoe().setMinzoom(realMin);
			if(g.maxZoom<ctx.getOptions().getMaxZoom()) 
				f.getTippecanoe().setMaxzoom(g.maxZoom);
			result.getFeatures().add(f);
			if(g.entries.size() > 1) {
				f.getProperties().setText(createGroupText(g));
			}
			f.getProperties().setArticleLength(null);
		}
		return LCContent.from(result);
	}

	private String createGroupText(Group g) {
		var subEntries = new ArrayList<>(g.entries);
		subEntries.remove(g.best);
		
		if(subEntries.size()<=10) {
			return subEntries.size()
				+ " locations:<ul>\n<li>"
				+ subEntries.stream()
					.sorted(Comparator.comparing(v->v.feature.getProperties().simpleLabel()))
					.map(v->
						"<a href=\""
						+ v.feature.getProperties().getLink()
						+ "\">"
						+ v.feature.getProperties().simpleLabel()
						+ "</a>"
					).collect(Collectors.joining("</li>\n<li>"))
				+ "</li>\n</ul>";
		}
		else {
			return subEntries.size()
				+ " locations:<ul>\n<li>"
				+ subEntries.stream()
					.sorted(Comparator.<Entry,Double>comparing(v->v.sortValue).reversed())
					.limit(9)
					.map(v->
						"<a href=\""
						+ v.feature.getProperties().getLink()
						+ "\">"
						+ v.feature.getProperties().simpleLabel()
						+ "</a>"
					).collect(Collectors.joining("</li>\n<li>"))
				+ "</li>\n<li>and "+(subEntries.size()-9)+" others…</li>\n</ul>";
		}
	}

	private void addEntry(List<Group> groups, Entry entry, double groupingDistance, int zoom) {
		if(entry.maxZoom != null && entry.maxZoom < zoom) return;
		
		Group closest = null;
		double closestDist = Double.MAX_VALUE;
		for(var g:groups) {
			var dist = distance(g, entry);
			if(g.maxZoom >= zoom && dist < groupingDistance && dist < closestDist) {
				closest = g;
				closestDist = dist;
			}
		}
		
		if(closest == null) {
			var g = new Group(zoom);
			g.maxZoom = zoom;
			g.setBest(entry);
			g.addEntry(entry);
			groups.add(g);
			return;
		}
		
		closest.entries.add(entry);
	}

	private double distance(Group g, Entry e) {
		return Math.sqrt((g.x-e.x)*(g.x-e.x)+(g.y-e.y)*(g.y-e.y));
	}

	public List<Entry> collectEntries(FeatureCollection col) {
		var entries = new ArrayList<Entry>();
		for(var feature : col.getFeatures()) {
			var loc = ((Point)feature.getGeometry()).getCoordinates();
			//assumes 100x100 map
			double x = 50/Math.PI*(Math.PI+Math.toRadians(loc.lng()));
			double y = 50/Math.PI*(Math.PI-Math.log(Math.tan(Math.PI/4+Math.toRadians(loc.lat())/2)));
			
			entries.add(new Entry(
				x,
				y,
				feature,
				feature.getProperties().getFilterMaxzoom()
			));
		}
		int maximumArticleLength = entries.stream().mapToInt(e->e.feature.getProperties().getArticleLength()).max().getAsInt();
		entries.forEach(e->e.calcSortValue(maximumArticleLength));
		Collections.sort(entries, Comparator.<Entry, Double>comparing(e->e.sortValue).reversed());
		
		return entries;
	}
	
	@RequiredArgsConstructor
	static class Entry {
		private final double x;
		private final double y;
		private final Feature feature;
		private final Integer maxZoom;
		private double sortValue;
		
		public void calcSortValue(double maximumArticleLength) {
			int base = switch(feature.getProperties().getIcon()) {
				case "city-major-capital" -> 9;
				case "city-large-capital" -> 8;
				case "city-medium-capital" -> 7;
				case "city-small-capital" -> 6;
				case "city-major" -> 5;
				case "city-large" -> 4;
				case "city-medium" -> 3;
				case "city-small" -> 2;
				case "location-other" -> -1;
				default -> 0;
			};
			sortValue = base + 0.9*feature.getProperties().getArticleLength()/maximumArticleLength;
		}
	}
	
	@RequiredArgsConstructor
	static class Group {
		private double x;
		private double y;
		private final int minZoom;
		private int maxZoom;
		private Entry best;
		private List<Entry> entries = new ArrayList<>();
		
		public void setBest(Entry e) {
			best = e;
			x = best.x;
			y = best.y;
		}
		
		public void addEntry(Entry e) {
			entries.add(e);
		}
	}
}
