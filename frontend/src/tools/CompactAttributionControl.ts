import { AttributionControl, Map } from "maplibre-gl";

export class CompactAttributionControl extends AttributionControl {

    private oldUpdateCompact: () => void;

    constructor(embedded:boolean) {
        super({compact: true});
        if(embedded) {
            this.oldUpdateCompact = this._updateCompact;
            this._updateCompact = ()=> {
                this.oldUpdateCompact();
                this._toggleAttribution();
            }
        }
    }
}