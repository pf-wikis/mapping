package io.github.pfwikis.layercompiler.steps;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.beust.jcommander.internal.Lists;

import io.github.pfwikis.JSMath;
import io.github.pfwikis.run.Tools;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GenerateLabelCenters extends LCStep {

    @Override
    public LCContent process() throws IOException {
    	LCContent in = getInput();
        if(!in.toJSONString().contains("\"Name\":")) {
            return LCContent.from("{\"type\":\"FeatureCollection\", \"features\": []}".getBytes(StandardCharsets.UTF_8));
        }

        log.info("  Generating label points from polygon centers");
        var commands = Lists.newArrayList(
            "-filter", "Name != null"
        );
        if(!"labels".equals(this.getName())) {
            commands.addAll(List.of("-dissolve", "Name", "copy-fields=inSubregion"));
        }
        commands.addAll(List.of(
            "-each", "filterMinzoom="+filterMinzoom(this.getName()),
            "-each", "filterMaxzoom=4+filterMinzoom",
            "-sort", "this.area", "descending"
        ));
        var tmp = Tools.mapshaper(in, commands.toArray());
        var labelPoints = Tools.geojsonPolygonLabels(tmp,
            "--precision=0.00001",
            "--include-area",
            "--label=center-of-mass",
            "--style=largest"
        );
        return labelPoints;
    }

    private String filterMinzoom(String name) {
        if(name.startsWith("region-labels")) return "1";
        if(name.startsWith("subregion-labels")) return "2";
        if(name.startsWith("nation-labels")) return "3";
        if(name.startsWith("province-labels")) return "4";
        if(name.startsWith("district-labels")) return "10";
        if(name.startsWith("continent-labels")) return "0";

        return JSMath.pixelSizeMinzoomFunction(
            300,
            "Math.max(this.width,this.height)*"
                +JSMath.degToMeters("(this.bounds[1]+this.bounds[3])/2")
        );
    }
}
