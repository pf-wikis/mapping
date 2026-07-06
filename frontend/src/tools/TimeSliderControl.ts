import { IControl } from 'maplibre-gl';
import { GolarionMap } from "./GolarionMap";
import style from 'virtual:style';

import { Options } from '../URLOptions.js';
import timeMeta, { TimeSlice } from '../../gen/timeMeta';
import throttle from '../utils/throttle';



export default class TimeSliderControl implements IControl {
  private map: GolarionMap;
  private container: HTMLElement;

  constructor(map: GolarionMap) {
    this.map = map;

    let updateMapUnthrottled = (time:TimeSlice) => map.setStateYear(time);
    let updateMap = throttle(updateMapUnthrottled, 100);

    // Create main container
    this.container = document.createElement('div');
    this.container.className = 'time-slider-container maplibregl-ctrl maplibregl-ctrl-group';

    map.options.onChange('year', (_, newYear:number) => {
      let time = timeMeta.byYear(newYear);
      updateMap(time);
    });
    /*
    if(map.startupOptions.embedded) {
      map.map.once('style.load', ()=>updateMapUnthrottled(timeMeta.byId(map.options.year)));
    }*/
  }
  
  onAdd(_:any): HTMLElement {
    this.container.innerHTML = `
    <input type="range" id="time-slider" name="time-slider" min="${timeMeta.oldest.id}" max="${timeMeta.latest.id}" value="${this.map.options.year}" />
    <label for="time-slider">${timeMeta.byYear(this.map.options.year).label}</label>
    `;
    const slider:HTMLInputElement = this.container.querySelector('#time-slider')!;
    const label:HTMLLabelElement = this.container.querySelector('label')!;
    const updateLabel = (time:TimeSlice) => {
      label.innerHTML = time.label;
    }

    this.map.options.onChange('year', (_, newYear:number) => {
      let time = timeMeta.byYear(newYear);
      slider.value = time.id.toString();
      updateLabel(time);
    });

    slider.addEventListener('input', () => {
        const value = parseInt(slider.value);
        this.map.options.year = timeMeta.byId(value).representativeYear;
    });
    return this.container;
  }

  onRemove(_:any): void {
    if (this.container && this.container.parentNode) {
      this.container.parentNode.removeChild(this.container);
    }
  }
}