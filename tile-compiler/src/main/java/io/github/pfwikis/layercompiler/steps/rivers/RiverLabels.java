package io.github.pfwikis.layercompiler.steps.rivers;

import io.github.pfwikis.layercompiler.steps.model.Inputs;
import io.github.pfwikis.layercompiler.steps.model.StepExecutor;
import io.github.pfwikis.layercompiler.steps.model.Time;
import io.github.pfwikis.layercompiler.steps.model.content.Content;
import io.github.pfwikis.layercompiler.steps.model.content.TimelessContent;
import io.github.pfwikis.run.Tools;

@Time.Requirement(Time.Requirement.Value.REQUIRES_SLICED)
public class RiverLabels extends StepExecutor {

    @Override
    public TimelessContent process(Inputs in) throws Exception {
        var res = Tools.mapshaper(this, in.getInput(),
            "-clean",
            "-dissolve", "label",
            "-filter", "Boolean(label)",
            "-each", "minzoom=5"
        );
        return Content.timeless(res);
    }

}
