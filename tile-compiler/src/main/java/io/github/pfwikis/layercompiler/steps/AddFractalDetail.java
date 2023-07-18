package io.github.pfwikis.layercompiler.steps;

import java.io.IOException;

import io.github.pfwikis.fractaldetailer.AddDetails;

public class AddFractalDetail extends LCStep {

    @Override
    public byte[] process() throws IOException {
        int maxDistance = ctx.getOptions().isProdDetail()?400:500;
        return AddDetails.addDetails(maxDistance, getInput());
    }

}
