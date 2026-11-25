package io.github.pfwikis.layercompiler;

import java.awt.Color;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.Getter;

@Getter
public enum ColorPalette {
	LAND      (new Color(248, 241, 225),      "land"),
    ICE       (new Color(1f, 1f, 1f, 0.75f),  "ice"),
    DISTRICTS (new Color(212, 204, 185),      "districts"),
    DESERTS   (new Color(255, 247, 190),      "deserts"),
    SWAMP     (new Color(183, 197, 188),      "swamp"),
    MOUNTAINS (new Color(222, 212, 184),      "mountains"),
    HILLS     (new Color(235, 227, 205),      "hills"),
    FORESTS   (new Color(187, 226, 198),      "forests"),
    SWAMPS    (new Color(183, 197, 188),      "swamps"),
    WATERS    (new Color(138, 180, 248),      "rivers", "shallow-waters", "waters"),
    BUILDINGS (new Color(119, 136, 153),      "buildings"),
    ROADS     (new Color(185, 157, 92),       "roads"),
    SPECIALS  (null,                          "specials"),
    CONTINENTS(new Color(248, 241, 225),      "continents"),
    GENERIC   (null,                          "generic"),
    //used in the frontend
	WATER_DEEP (new Color(110, 160, 245), "waterDeep"),
	BORDERS_REGION (new Color(107,  42,  33), "bordersRegion"),
	BORDERS_NATION (new Color(170, 170, 170), "bordersNation"),
	LABELS_REGION (new Color( 17,  42,  97), "labelsRegion"),
	WHITE (new Color(255, 255, 255), "white"),
	BLACK (new Color( 10,  10,  10), "black"),
	LABELS_REGION_OUTER (new Color(213, 195, 138), "labelsRegionOuter");

    private static final Map<String, ColorPalette> BY_LAYER = Arrays.stream(values())
            .flatMap(lc -> Arrays.stream(lc.layerNames)
                    .map(name -> Map.entry(name, lc)))
            .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));

    private final Color color;
    private final String[] layerNames;

    ColorPalette(Color color, String... layerNames) {
        this.color = color;
        this.layerNames = layerNames;
    }

    public static ColorPalette forLayer(String layer) {
        var result = BY_LAYER.get(layer);
        if (result == null) {
            throw new IllegalStateException("Unknown color for " + layer);
        }
        return result;
    }
}