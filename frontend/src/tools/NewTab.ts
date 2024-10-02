
import { IControl, Map } from "maplibre-gl";

export default class NewTab implements IControl {
    onAdd(map: Map): HTMLElement {
        let container = document.createElement('div');
        container.className = 'maplibregl-ctrl maplibregl-ctrl-group';

        let button = document.createElement('button');
        button.className = 'maplibregl-ctrl-fullscreen';
        button.type = 'button';
        container.appendChild(button);

        let span = document.createElement('span');
        span.className = 'maplibregl-ctrl-icon';
        span.setAttribute('aria-hidden', 'true');
        button.appendChild(span);

        button.addEventListener('click', this._openInTab);

        return container;
    }
    onRemove(map: Map): void {}

    _openInTab() {
        window.open(window.location.href.replace('&embedded=true', ''), '_blank').focus();
    }
}
