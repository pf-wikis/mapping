package io.github.pfwikis.model;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class City {
    private String fullUrl;
    private String pageName;
    private BigDecimal coordsLon;
    private BigDecimal coordsLat;
    private String population;
    private String size;
    private boolean capital;
    private String name;
    private String ibtype;

    @JsonCreator
    public static City fromJson(ObjectNode n) {
        var c = new City();
        String name = n.fieldNames().next();
        var fields = (ObjectNode) n.get(name).get("printouts");
        c.setName(name.substring(name.indexOf('#')+1));
        c.setPageName(name.substring(0,name.indexOf('#')));
        c.setFullUrl(n.get(name).get("fullurl").asText());

        var pop = (ArrayNode)fields.get("Has population");
        if(!pop.isEmpty()) c.setPopulation(pop.get(0).asText());
        var ibtype = (ArrayNode)fields.get("Has infobox type");
        if(!ibtype.isEmpty()) c.setIbtype(ibtype.get(0).asText());
        var size = (ArrayNode)fields.get("Is size");
        if(!size.isEmpty()) c.setSize(size.get(0).asText());
        var cap = (ArrayNode)fields.get("Is capital");
        if(!cap.isEmpty()) c.setCapital("t".equals(cap.get(0).asText()));
        var detName = (ArrayNode)fields.findPath("Has name");
        if(!detName.isEmpty()) c.setName(detName.get(0).asText());
        var coord = (ArrayNode)fields.get("Has coordinates");
        if(!coord.isEmpty()) {
            c.setCoordsLon(coord.get(0).get("lon").decimalValue());
            c.setCoordsLat(coord.get(0).get("lat").decimalValue());
        }
        return c;
    }
}
