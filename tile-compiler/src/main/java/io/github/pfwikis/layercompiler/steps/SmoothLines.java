package io.github.pfwikis.layercompiler.steps;

import java.io.IOException;

import io.github.pfwikis.layercompiler.steps.model.LCContent;
import io.github.pfwikis.layercompiler.steps.model.LCStep;
import io.github.pfwikis.run.Tools;

public class SmoothLines extends LCStep {

    @Override
    public LCContent process() throws IOException {
        return Tools.mapshaper(getInput(),
            "-require", "curve-interpolator", "alias=_",
            "-explode",
            "-each", """
                let json = this.geojson;

                let Ps = json.geometry.coordinates;
                let l = Ps.length;

                let interp = new _.CurveInterpolator(Ps, { tension: this.properties.noSmooth?1.0:0.25 });

                var Ts = Ps.map((_, i) => i / (l - 1));
                var Us = Ts.map(t => _.getTtoUmapping(t, interp.arcLengths));

                let result = [];
                for(let i=0;i<l;i++) {
                    result.push(interp.getPointAt(Us[i]));
                    if(i<l-1) {
                        """+interpolate(10)+"""
                    }
                }
                json.geometry.coordinates = result;
                this.geojson=json;
            """
        );
    }

    private String interpolate(int number) {
        var sb = new StringBuilder();
        for(int i=0;i<number;i++) {
            sb.append("result.push(interp.getPointAt(("+(number-i)+"*Us[i]+"+(i+1)+"*Us[i+1])/"+(number+1)+"));");
        }
        return sb.toString();
    }
}
