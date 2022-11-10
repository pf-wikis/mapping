package io.github.pfwikis;

public class JSMath {
    public static String degToMeters(String lat) {
        return "111319.491*"+scaleFactor(lat);
    }

    public static String scaleFactor(String lat) {
        return "(1 + 0.00001120378*(Math.cos(2*"+lat+"/180*Math.PI) - 1)) / Math.cos("+lat+"/180*Math.PI)";
    }

    public static String pixelSizeMinzoomFunction(double minPixelSize, String attribute) {
        return "Math.floor(Math.log2(($threshold)/($attribute)))"
            .replace("$threshold", Double.toString(minPixelSize*111319.491))
            .replace("$attribute", attribute);
    }
}
