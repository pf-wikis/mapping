package io.github.pfwikis.layercompiler.steps;

import java.io.File;

import io.github.pfwikis.layercompiler.description.Ctx;
import io.github.pfwikis.layercompiler.steps.model.Inputs;
import io.github.pfwikis.layercompiler.steps.model.StepExecutor;
import io.github.pfwikis.layercompiler.steps.model.Time;
import io.github.pfwikis.layercompiler.steps.model.content.Content;
import io.github.pfwikis.run.Tools;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Time.Requirement(Time.Requirement.Value.ANY)
public class CompileSprites extends StepExecutor {
	
    @Override
    public Content process(Inputs in) throws Exception {
    	log.info("Compiling Sprites");
    	var spriteDir = new File(Ctx.INSTANCE.getOptions().targetDirectory(), "sprites");
    	spriteDir.mkdirs();
        Tools.spriteZero(null, new File(spriteDir, "sprites"), new File("sprites/"));
        Tools.spriteZero(null, new File(spriteDir, "sprites@2x"), new File("sprites/"), "--retina");
        return Content.empty();
    }

}
