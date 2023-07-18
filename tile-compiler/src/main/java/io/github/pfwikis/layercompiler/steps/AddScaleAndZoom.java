package io.github.pfwikis.layercompiler.steps;

import java.io.IOException;
import java.util.Set;

import io.github.pfwikis.run.Tools;

public class AddScaleAndZoom extends LCStep {

    @Override
    public byte[] process() throws IOException {

        if("cities".equals(getName())) {
            var result = Tools.mapshaper(getInput(), "-each", """
                filterMinzoom=(() => {switch(size) {
                    case 0: return 1;
                    case 1: return 2;
                    case 2: return 3;
                    default: return 3;
                }})()
            """);
            return result;
        }
        else if("locations".equals(getName())) {
            return Tools.mapshaper(getInput(), "-each", "filterMinzoom=2");
        }
        else if(Set.of("districts", "district-labels").contains(getName())) {
            return Tools.mapshaper(getInput(), "-each", "filterMinzoom=4");
        }
        else {
            throw new IllegalStateException("Unhandled layer "+getName());
        }

    }

}
