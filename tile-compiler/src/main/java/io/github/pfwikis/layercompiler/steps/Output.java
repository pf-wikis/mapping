package io.github.pfwikis.layercompiler.steps;

import java.io.File;
import java.io.IOException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.io.Files;

import io.github.pfwikis.run.Tools;

public class Output extends LCStep {

    private final ObjectMapper om = new ObjectMapper();
    private final ObjectWriter printer = om.writer().withDefaultPrettyPrinter();

    @Override
    public LCContent process() throws IOException {
        var value = createTippecanoeProperties();

        byte[] result;
        if(!ctx.getOptions().isProdDetail()) {
            result = printer.writeValueAsBytes(value);
        }
        else {
            result = om.writeValueAsBytes(value);
        }
        Files.write(result, new File(ctx.getGeo(), getName()+".geojson"));
        return LCContent.from(value);
    }

    private JsonNode createTippecanoeProperties() throws IOException {
        var withProp = Tools.mapshaper(getInput(),
            "-each", """
                tippecanoe = {};
                if(typeof filterMinzoom === 'number') {
                    tippecanoe.minzoom = Math.max(Math.min(filterMinzoom, $maxzoom), 0);
                }
                if(typeof filterMaxzoom === 'number') {
                    tippecanoe.maxzoom = Math.max(filterMaxzoom, 1);
                }
            """.replace("$maxzoom", Integer.toString(ctx.getOptions().getMaxZoom()))
        );

        var tree = withProp.toJSONNode(); 
        var arr = (ArrayNode)tree.required("features");
        for(var n:arr) {
            var props = (ObjectNode)n.required("properties");
            var tippecanoe = props.remove("tippecanoe");
            if(tippecanoe != null && !tippecanoe.isNull()) {
                ((ObjectNode)n).set("tippecanoe", tippecanoe);
            }
            //remove null values
            var it=props.properties().iterator();
            it.forEachRemaining(e->{
            	if(e.getValue().isNull())
            		it.remove();
            });
        }
        return tree;
    }

}
