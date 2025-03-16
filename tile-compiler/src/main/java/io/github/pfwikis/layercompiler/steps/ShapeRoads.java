package io.github.pfwikis.layercompiler.steps;

import io.github.pfwikis.layercompiler.steps.model.LCContent;
import io.github.pfwikis.layercompiler.steps.model.LCStep;
import io.github.pfwikis.run.Tools;
import lombok.Setter;

@Setter
public class ShapeRoads extends LCStep {
	
    @Override
    public LCContent process() throws Exception {
    	String formula = """
    		"width"
    		*(
		  		(1.0+0.00001120378*(cos(2.0*radians(y(centroid(@geometry))))-1))
		  		/cos(radians(y(centroid(@geometry))))/111319.491/2.0
    		)
		""".trim().replaceAll("\\s+", ""); 
    			
    	
        return Tools.qgis(this, "native:buffer", getInput(),
            "--DISTANCE=expression:"+formula,
            "--DISSOLVE=true",
            "--SEPARATE_DISJOINT=true"
        );
    }

}
