package io.github.pfwikis.layercompiler.steps;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.stream.Collectors;

import io.github.pfwikis.layercompiler.steps.model.LCContent;
import io.github.pfwikis.layercompiler.steps.model.LCStep;
import io.github.pfwikis.run.Tools;

public class LocationTypeToIcon extends LCStep {

    @Override
    public LCContent process() throws IOException {
    	var icons = Arrays.stream(new File("sprites").list())
    		.filter(n->n.startsWith("location-") && n.endsWith(".svg"))
    		.map(n->n.substring(9, n.length()-4))
    		.map(n->"if(this.properties.type==='"+n+"') {this.properties.icon='location-"+n+"';}")
    		.collect(Collectors.joining("\nelse "));
    	
        return Tools.mapshaper(getInput(),
            "-each", icons+"""
            	\nelse {this.properties.icon='location-other';}
            	delete this.properties.type;
            """
        );
    }
}
