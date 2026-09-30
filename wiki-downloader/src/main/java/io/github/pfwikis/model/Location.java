package io.github.pfwikis.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.function.Consumer;

import com.fasterxml.jackson.annotation.JsonCreator;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Location implements Comparable<Location> {
    private String pageName;
    private BigDecimal coordsLon;
    private BigDecimal coordsLat;
    private String population;
    private String size;
    private Boolean capital;
    private String name;
    private String ibtype;
    private String loiType;
    private Instant modificationDate;

    @JsonCreator
    public static Location fromJson(ObjectNode n) {
        var c = new Location();
        String name = n.propertyNames().iterator().next();
        var fields = (ObjectNode) n.get(name).get("printouts");
        c.setName(name.substring(name.indexOf('#')+1));
        c.setPageName(name.substring(0,name.indexOf('#')));

        parse(fields,"Has population", v->c.setPopulation(v.asString()));
        parse(fields,"Has infobox type", v->c.setIbtype(v.asString()));
        parse(fields,"Is size", v->c.setSize(v.asString()));
        parse(fields,"Is capital", v->c.setCapital("t".equals(v.asString())));
        parse(fields,"Has name", v->c.setName(v.asString()));
        parse(fields,"Has coordinates", v->{
        	c.setCoordsLon(v.get("lon").decimalValue());
            c.setCoordsLat(v.get("lat").decimalValue());
        });
        parse(fields,"Has location type", v->c.setLoiType(v.asString()));
        parse(fields,"Modification date", v->c.setModificationDate(Instant.ofEpochSecond(v.findPath("timestamp").asLong())));
        return c;
    }
    
    private static void parse(ObjectNode in, String prop, Consumer<JsonNode> ifPresent) {
    	var arr = in.findPath(prop);
    	if(!arr.isArray() || arr.isEmpty()) return;
    	ifPresent.accept(arr.get(0));
    }

	@Override
	public int compareTo(Location o) {
		return name.compareTo(o.name);
	}
}
