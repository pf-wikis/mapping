package io.github.pfwikis.layercompiler.steps;

import java.io.IOException;

import io.github.pfwikis.fractaldetailer.AddDetails;

public class AddFractalDetail extends LCStep {

    @Override
    public byte[] process() throws IOException {
        double maxDistance = ctx.getOptions().isProdDetail()?.10:.25;
        return AddDetails.addDetails(maxDistance, getInput());
    }

}
