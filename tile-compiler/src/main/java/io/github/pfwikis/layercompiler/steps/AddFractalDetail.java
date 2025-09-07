package io.github.pfwikis.layercompiler.steps;

import java.io.File;
import java.io.IOException;

import io.github.pfwikis.fractaldetailer.AddDetails;
import io.github.pfwikis.layercompiler.steps.model.LCContent;
import io.github.pfwikis.layercompiler.steps.model.LCStep;

public class AddFractalDetail extends LCStep {

    @Override
    public LCContent process() throws IOException {
        double maxDistance = ctx.getOptions().isProdDetail()?.02:.2;
        
        //LCContent.MAPPER.writeValue(new File("debug/"+this.getName()+"_in.json"), getInput().toFeatureCollection());
        
        var result = LCContent.from(AddDetails.addDetails(maxDistance, getInput().toFeatureCollection()));
        
        //LCContent.MAPPER.writeValue(new File("debug/"+this.getName()+"_out.json"), result.toFeatureCollection());
        
        return result;
    }

}
