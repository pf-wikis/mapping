package io.github.pfwikis.layercompiler.steps;

import java.io.IOException;

import io.github.pfwikis.fractaldetailer.AddDetails;
import io.github.pfwikis.layercompiler.steps.model.LCContent;
import io.github.pfwikis.layercompiler.steps.model.LCStep;

public class AddFractalDetail extends LCStep {

    @Override
    public LCContent process() throws IOException {
        double maxDistance = ctx.getOptions().isProdDetail()?.10:.25;
        return LCContent.from(AddDetails.addDetails(maxDistance, getInput().toFeatureCollection()));
    }

}
