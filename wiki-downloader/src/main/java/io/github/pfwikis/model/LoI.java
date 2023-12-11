package io.github.pfwikis.model;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class LoI {
    private String pageName;
    private BigDecimal coordsLon;
    private BigDecimal coordsLat;
    private String type;
    private String name;
    private String text;

    @JsonCreator
    public static LoI fromJson(ObjectNode n) {
        var c = new LoI();
        String name = n.fieldNames().next();
        var fields = (ObjectNode) n.get(name).get("printouts");
        c.setName(name.substring(name.indexOf('#')+1));
        c.setPageName(name.substring(0,name.indexOf('#')));

        var type = (ArrayNode)fields.get("Has location type");
        if(!type.isEmpty()) c.setType(type.get(0).asText());
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
