import { Prop } from "../../gen/props-meta-golarion.ts";
import { state } from "../ml-style/state.ts";
import type { Expression } from "../ml-style/expression";

export const timeIndexStart:Expression<boolean> = ['any', ['!', ['has', Prop.timeIndexStart]], ['>=', state.timeIndex.get(), ['get', Prop.timeIndexStart]]];
export const timeIndexEnd:Expression<boolean> = ['any', ['!', ['has', Prop.timeIndexEnd]],   ['<',  state.timeIndex.get(), ['get', Prop.timeIndexEnd]]];