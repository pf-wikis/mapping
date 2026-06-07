package io.github.pfwikis.layercompiler.steps;

import java.io.IOException;

import io.github.pfwikis.fractaldetailer.AddDetails;
import io.github.pfwikis.layercompiler.description.Ctx;
import io.github.pfwikis.layercompiler.steps.model.Inputs;
import io.github.pfwikis.layercompiler.steps.model.StepExecutor;
import io.github.pfwikis.layercompiler.steps.model.Time;
import io.github.pfwikis.layercompiler.steps.model.content.Content;
import io.github.pfwikis.layercompiler.steps.model.data.GeoData;

@Time.Requirement(Time.Requirement.Value.ANY)
public class AddFractalDetail extends StepExecutor {

    @Override
    public Content process(Inputs in) throws IOException {
        double maxDistance = Ctx.INSTANCE.getOptions().isProdDetail()?.02:.2;
        
        //LCContent.MAPPER.writeValue(new File("debug/"+this.getName()+"_in.json"), getInput().toFeatureCollection());
        
        var result = GeoData.from(AddDetails.addDetails(maxDistance, in.getInput().toFeatureCollection()));
        
        //LCContent.MAPPER.writeValue(new File("debug/"+this.getName()+"_out.json"), result.toFeatureCollection());
        
        return Content.derivedFrom(in, result);
    }

}
