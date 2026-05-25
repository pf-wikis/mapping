import { IControl, Map } from 'maplibre-gl';
import { GolarionMap } from "./GolarionMap";
import style from 'virtual:style';
import throttle from '../utils/throttle';
import options from '../URLOptions.js';

export default class TimeSliderControl implements IControl {
  private map: GolarionMap;
  private container: HTMLElement;

  constructor(map: GolarionMap) {
    this.map = map;

    // Create main container
    this.container = document.createElement('div');
    this.container.className = 'time-slider-container maplibregl-ctrl maplibregl-ctrl-group';
  }

  updateMap = throttle((year:number) => this.map.map.setGlobalStateProperty('year', year), 100);
  
  onAdd(map: Map): HTMLElement {
    this.container.innerHTML = `
    <input type="range" id="time-slider" name="time-slider" min="4710" max="${style.state?.year?.default}" value="${options.year??style.state?.year?.default}" />
    <label for="time-slider"></label>
    `;
    const slider:HTMLInputElement = this.container.querySelector('#time-slider')!;
    const label:HTMLLabelElement = this.container.querySelector('label')!;

    const updateYear = () => {
        const value = slider.value;
        this.updateMap(parseInt(value));
        label.textContent = `Year ${value} AR`;
    }
    slider.addEventListener('input', updateYear);
    map.once('style.load', updateYear);
    return this.container;
  }

  onRemove(map: Map): void {
    if (this.container && this.container.parentNode) {
      this.container.parentNode.removeChild(this.container);
    }
  }
}