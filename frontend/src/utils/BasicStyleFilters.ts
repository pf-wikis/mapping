import { Prop } from "../../gen/props-meta-golarion";
import { state } from "../ml-style/state";
import { Expression } from "../ml-style/expression";

export const timeIndexStart:Expression<boolean> = ['any', ['!', ['has', Prop.timeIndexStart]], ['>=', state.timeIndex.get(), ['get', Prop.timeIndexStart]]];
export const timeIndexEnd:Expression<boolean> = ['any', ['!', ['has', Prop.timeIndexEnd]],   ['<',  state.timeIndex.get(), ['get', Prop.timeIndexEnd]]];