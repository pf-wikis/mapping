package io.github.pfwikis.layercompiler.steps;

import java.io.IOException;

import io.github.pfwikis.run.Tools;

public class PrettyPrint extends LCStep {

    @Override
    public byte[] process(Ctx ctx, byte[] f) throws IOException {
        return Tools.jq(f, ".");
    }
}
