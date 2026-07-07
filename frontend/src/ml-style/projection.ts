import { ProjectionSpecification } from "maplibre-gl";

export const projection:ProjectionSpecification = {
    "type": [
    "interpolate",
    ["linear"],
    ["zoom"],
    4,
    "vertical-perspective",
    5,
    "mercator"
    ]
};