import { IControl, Map as MLMap } from 'maplibre-gl';
import { GolarionMap } from "./GolarionMap";
import style from 'virtual:style';
import throttle from '../utils/throttle';
import options from '../URLOptions.js';
import timeMeta from '../utils/timeMeta';

export default class TimeSliderControl implements IControl {
  private map: GolarionMap;
  private container: HTMLElement;
  private initialTimeIndex: number;

  constructor(map: GolarionMap, initialTimeIndex: number) {
    this.map = map;
    this.initialTimeIndex = initialTimeIndex;

    // Create main container
    this.container = document.createElement('div');
    this.container.className = 'time-slider-container maplibregl-ctrl maplibregl-ctrl-group';
    if(options.embedded) {
      map.map.once('style.load', ()=>this.updateMapUnthrottled(initialTimeIndex));
    }
  }

  updateMapUnthrottled = (timeIndex:number) => this.map.map.setGlobalStateProperty('timeIndex', timeIndex);
  updateMap = throttle(this.updateMapUnthrottled, 100);
  
  onAdd(map: MLMap): HTMLElement {
    this.container.innerHTML = `
    <input type="range" id="time-slider" name="time-slider" min="${timeMeta.min}" max="${timeMeta.max}" value="${this.initialTimeIndex}" />
    <label for="time-slider"></label>
    `;
    const slider:HTMLInputElement = this.container.querySelector('#time-slider')!;
    const label:HTMLLabelElement = this.container.querySelector('label')!;

    const updateYear = () => {
        const value = parseInt(slider.value);
        this.updateMap(value);
        label.innerHTML = timeMeta.byId.get(value)?.label || `Unlabeled entry ${value}`;
    }
    slider.addEventListener('input', updateYear);
    map.once('style.load', updateYear);
    return this.container;
  }

  onRemove(map: MLMap): void {
    if (this.container && this.container.parentNode) {
      this.container.parentNode.removeChild(this.container);
    }
  }
}