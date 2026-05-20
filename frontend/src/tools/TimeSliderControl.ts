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
    this.container.className = 'time-slider-container';
  }
  
  onAdd(map: Map): HTMLElement {
    this.container.innerHTML = `
    <input type="range" id="time-slider" name="time-slider" min="4700" max="${style.state?.year?.default}" value="${options.year??style.state?.year?.default}" />
    <label for="time-slider"></label>
    `;
    const slider:HTMLInputElement = this.container.querySelector('#time-slider')!;
    const label:HTMLLabelElement = this.container.querySelector('label')!;

    let updateMap = throttle((year:number) => map.setGlobalStateProperty('year', year), 200);

    function updateYear() {
        const value = slider.value;
        console.log(value);
        updateMap(parseInt(value));
        label.textContent = `Year ${value} AR`;
    }
    slider.addEventListener('input', updateYear);
    map.on('style.load' , () => {
        updateYear();
    });
    return this.container;
  }

  onRemove(map: Map): void {
    if (this.container && this.container.parentNode) {
      this.container.parentNode.removeChild(this.container);
    }
  }
}