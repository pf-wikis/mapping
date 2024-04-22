package io.github.pfwikis.layercompiler.steps;

import java.io.IOException;

import io.github.pfwikis.layercompiler.steps.model.LCContent;
import io.github.pfwikis.layercompiler.steps.model.LCStep;
import io.github.pfwikis.run.Tools;

public class CityTypeToIcon extends LCStep {

    @Override
    public LCContent process() throws IOException {
        return Tools.mapshaper(getInput(),
            "-each","""
            	let suffix = '';
            	if(this.properties.capital) suffix = '-capital';
            	if(this.properties.size === 1) this.properties.icon='city-large'+suffix;
            	else if(this.properties.size === 2) this.properties.icon='city-medium'+suffix;
            	else if(this.properties.size === 3) this.properties.icon='city-small'+suffix;
            	else this.properties.icon='city-major'+suffix;
            	delete this.properties.capital;
            	delete this.properties.size;
            	delete suffix;
            """
        );
    }
}
