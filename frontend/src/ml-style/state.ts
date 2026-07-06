import timeMeta from "../../gen/timeMeta";
import { ToString, Widen } from "../utils/type-utils";
import { Expression, GlobalState } from "./expression";

export const defaultState = {
  timeIndex: timeMeta.latest.id,
  rotated: false,
  showLabels: 'visible' as 'visible'|'none',
  showLocations: 'visible' as 'visible'|'none',
  showBorders: 'visible' as 'visible'|'none'
} as const;

export type StateProp = keyof typeof defaultState;
export type StateTypes = {[K in StateProp]:Widen<typeof defaultState[K]>};

class StateSpec<K extends StateProp, V extends StateTypes[K]> {
  name: K;
  default: V;
  enableDebug = false;

  constructor(name: K, defaultValue:V) {
    this.name = name;
    this.default = defaultValue;
  }

  get():GlobalState<K>|[ToString<V>, GlobalState<K>] {
    let expr:any = ['global-state', this.name];
    if(this.enableDebug)
        return [(typeof this.default), expr] as any;
    return expr;
  }
}

export const state = Object.fromEntries(
    Object.keys(defaultState).map((k) => [
        k,
        new StateSpec(k as StateProp, defaultState[k as StateProp])
    ])
) as {
    [K in StateProp]: StateSpec<K, StateTypes[K]>
};



