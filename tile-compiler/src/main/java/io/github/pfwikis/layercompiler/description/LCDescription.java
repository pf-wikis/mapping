package io.github.pfwikis.layercompiler.description;

import java.util.List;

import lombok.Getter;
import lombok.Setter;


@Getter @Setter
public class LCDescription {
    private String name;
    private List<LCDescriptionStep> steps;

    public String createStepId(int index) {
    	String step = steps.get(index).getStep();
        String result =  name+"."+step;
        var similar = steps.stream().filter(s->s.getStep().equals(step)).toList();
        if(similar.size() == 1)
        	return result;
        else
        	return result+"_"+(1+similar.indexOf(steps.get(index)));
    }
}
