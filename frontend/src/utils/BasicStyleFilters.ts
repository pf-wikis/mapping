import { ExpressionSpecification } from "maplibre-gl";
import { Prop } from "../../gen/props-meta-golarion";

export const timeIndexStart:ExpressionSpecification = ['any', ['!', ['has', 'timeIndexStart']], ['>=', ['global-state', 'timeIndex'], ['get', Prop.timeIndexStart]]];
export const timeIndexEnd:ExpressionSpecification = ['any', ['!', ['has', 'timeIndexEnd']],   ['<',  ['global-state', 'timeIndex'], ['get', Prop.timeIndexEnd]]];