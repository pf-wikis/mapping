package io.github.pfwikis.layercompiler.steps;

import java.io.IOException;

import io.github.pfwikis.run.Tools;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class AddZoom extends LCStep {

    private final Integer minZoom;
    private final Integer maxZoom;

    @Override
    public byte[] process() throws IOException {
        var props = new StringBuilder();
        if(minZoom != null) {
            props.append(" filterMinzoom="+minZoom+";");
        }
        if(maxZoom != null) {
            props.append(" filterMaxzoom="+maxZoom+";");
        }
        if(props.isEmpty())
            return getInput();

        return Tools.mapshaper(getInput(), "-each", props.toString());
    }
}
