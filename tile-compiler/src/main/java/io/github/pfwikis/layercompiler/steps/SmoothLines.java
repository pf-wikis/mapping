package io.github.pfwikis.layercompiler.steps;

import java.io.IOException;

import io.github.pfwikis.run.Tools;

public class SmoothLines extends LCStep {

    @Override
    public byte[] process() throws IOException {
        return Tools.mapshaper(getInput(),
            "-require", "curve-interpolator@3.0.1", "alias=_",
            "-explode",
            "-each", """
                if(this.properties.noSmooth) return;

                let json = this.geojson;

                let Ps = json.geometry.coordinates;
                let l = Ps.length;

                let interp = new _.CurveInterpolator(Ps, { tension: 0.25 });

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
