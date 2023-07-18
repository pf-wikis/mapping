package io.github.pfwikis.layercompiler.steps;

import java.io.IOException;

import io.github.pfwikis.run.Tools;

public class AddCityZoom extends LCStep {

    @Override
    public byte[] process() throws IOException {
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

}
