package io.github.pfwikis.layercompiler.steps;

import io.github.pfwikis.layercompiler.steps.model.Inputs;
import io.github.pfwikis.layercompiler.steps.model.StepExecutor;
import io.github.pfwikis.layercompiler.steps.model.Time;
import io.github.pfwikis.layercompiler.steps.model.content.Content;
import io.github.pfwikis.run.Tools;
import lombok.Setter;

@Setter
@Time.Requirement(Time.Requirement.Value.REQUIRES_SLICED)
public class ShapeRoads extends StepExecutor {
	
    @Override
    public Content process(Inputs in) throws Exception {
    	String formula = """
    		"width"
    		*(
		  		(1.0+0.00001120378*(cos(2.0*radians(y(centroid(@geometry))))-1))
		  		/cos(radians(y(centroid(@geometry))))/111319.491/2.0
    		)
		""".trim().replaceAll("\\s+", ""); 
    			
    	
        return Content.timeless(Tools.qgis(this, "native:buffer", in.getInput(),
            "--DISTANCE=expression:"+formula,
            "--DISSOLVE=true",
            "--SEPARATE_DISJOINT=true"
        ));
    }

}
