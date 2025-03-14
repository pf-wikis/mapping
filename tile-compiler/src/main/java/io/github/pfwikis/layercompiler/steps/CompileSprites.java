package io.github.pfwikis.layercompiler.steps;

import java.io.File;

import io.github.pfwikis.layercompiler.steps.model.LCContent;
import io.github.pfwikis.layercompiler.steps.model.LCStep;
import io.github.pfwikis.run.Tools;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CompileSprites extends LCStep {
	
    @Override
    public LCContent process() throws Exception {
    	log.info("Compiling Sprites");
    	var spriteDir = new File(ctx.getOptions().targetDirectory(), "sprites");
    	spriteDir.mkdirs();
        Tools.spriteZero(null, new File(spriteDir, "sprites"), new File("sprites/"));
        Tools.spriteZero(null, new File(spriteDir, "sprites@2x"), new File("sprites/"), "--retina");
        return LCContent.empty();
    }

}
