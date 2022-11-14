package io.github.pfwikis.layercompiler.steps;

import java.io.IOException;

public class StopProcessing extends LCStep {

    @Override
    public byte[] process(Ctx ctx, byte[] f) throws IOException {
        return null;
    }
}
