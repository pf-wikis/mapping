package io.github.pfwikis.layercompiler.description;

import java.util.List;

import lombok.Getter;
import lombok.Setter;


@Getter @Setter
public class LCDescription {
    private String name;
    private List<StepDescription> steps;

    public String createName(int i) {
    	String step = steps.get(i).getStep();
        String result =  name+"."+step;
        var similar = steps.stream().filter(s->s.getStep().equals(step)).toList();
        if(similar.size() == 1)
        	return result;
        else
        	return result+"_"+(1+similar.indexOf(steps.get(i)));
    }
}
