package io.github.pfwikis.layercompiler.description;

import java.util.List;

import lombok.Getter;
import lombok.Setter;


@Getter @Setter
public class LCDescription {
    private String name;
    private List<StepDescription> steps;

    public String createName(int i) {
        return name+"."+steps.get(i).getStep();
    }
}
