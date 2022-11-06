package io.github.pfwikis.layercompiler.steps;

import java.io.File;
import java.io.IOException;

import io.github.pfwikis.fractaldetailer.AddDetails;

public class AddFractalDetail extends LCStep {

    @Override
    public File process(Ctx ctx, File f) throws IOException {
        var tmp = tmpGeojson();
        int maxDistance = ctx.getOptions().isProdDetail()?250:500;
        AddDetails.addDetails(maxDistance, f, tmp);
        return tmp;
    }

}
