package io.github.pfwikis.layercompiler;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

import org.apache.commons.io.FileUtils;

import io.github.pfwikis.layercompiler.steps.*;
import io.github.pfwikis.layercompiler.steps.LCStep.Ctx;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;

@Log
@Getter
@RequiredArgsConstructor
public class LayerCompiler {

    private final Ctx ctx;
    private final List<LayerCompiler> dependencies = new ArrayList<>();
    private final CountDownLatch completion = new CountDownLatch(1);
    private final List<LCStep> steps = new ArrayList<>();
    private byte[] finalResult;

    public void init() {
        steps.add(new GenerateLabelCenters());
        if("borders".equals(ctx.getName())) {
            steps.add(new GenerateBorderVariants());
        }
        if("districts".equals(ctx.getName())) {
            steps.add(new AddDistrictGap());
        }
        if(Set.of("chasms", "continents", "deserts", "forests", "hills", "ice", "mountains", "swamps", "waters").contains(ctx.getName())) {
            steps.add(new AddFractalDetail());
        }
        //smooth rivers
        //does not work as intended right now
        //mv 'geo/rivers.geojson' 'geo/tmp.geojson'
        //qgis_process run native:smoothgeometry --distance_units=meters --area_units=m2 --ellipsoid=EPSG:7030 --ITERATIONS=3 --OFFSET=0.25 --MAX_ANGLE=180 --INPUT='geo/tmp.geojson' --OUTPUT='geo/rivers.geojson'
        steps.add(new AddScaleAndZoom());
        steps.add(new CreateTippecanoeProperty());
        if(!ctx.getOptions().isProdDetail()) {
            steps.add(new PrettyPrint());
        }
    }

    public void compile() {
        log.info("Compiling file "+ctx.getName());
        for(var dep : dependencies) {
            log.info("  waiting for "+dep.getCtx().getName());
            try {
                dep.getCompletion().await();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        byte[] current = ctx.getInput();
        try {
            for(var step:steps) {
                log.info("  Step "+step.getClass().getSimpleName());
                current = step.process(ctx, current);
                if(current == null) {
                    return;
                }
            }

            File target = new File(ctx.getGeo(), ctx.getName()+".geojson");
            FileUtils.writeByteArrayToFile(target, current);
            finalResult = current;
            completion.countDown();
        } catch(Exception e) {
            throw new RuntimeException("Failed to process "+ctx.getName()+" in "+current, e);
        }
    }
}
