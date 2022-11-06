package io.github.pfwikis.layercompiler.steps;

import java.io.File;
import java.io.IOException;

import io.github.pfwikis.Tools;

public class PrettyPrint extends LCStep {

    @Override
    public File process(Ctx ctx, File f) throws IOException {
        var tmp = tmpGeojson();
        Tools.runAndPipeTo(tmp, "jq", ".", f);
        return tmp;
    }
}
