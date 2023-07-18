package io.github.pfwikis.layercompiler.steps.rivers;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class SmoothInterpolator {

    private final double[] xPoints;
    private final double[] yPoints;

    public double value(double x) {
        if(x<xPoints[0]) throw new IllegalArgumentException(Double.toString(x));
        if(x>xPoints[xPoints.length-1]) throw new IllegalArgumentException(Double.toString(x));

        for(int i=0;i<xPoints.length-1;i++) {
            if(x<=xPoints[i+1]) {
                double normX = (x-xPoints[i])/(xPoints[i+1]-xPoints[i]);

                double v = -(Math.cos(Math.PI * normX) - 1) / 2;
                return v*(yPoints[i+1]-yPoints[i])+yPoints[i];
            }
        }
        throw new IllegalStateException();
    }
}
