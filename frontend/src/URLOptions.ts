let options = window.location.hash?new URLSearchParams(window.location.hash.substring(1)):new URLSearchParams();
let projectionOptions = ['auto', 'globe', 'mercator'];

let projection = options.get('projection')??'auto';
let zoom = options.get('zoom');
let year = options.get('year');

export default {
    embedded: options.get('embedded') === 'true',
    hideLabels: options.get('hideLabels') === 'true',
    hideLocations: options.get('hideLocations') === 'true',
    hideBorders: options.get('hideBorders') === 'true',
    projection: (projectionOptions.includes(projection) ? projection : 'auto') as 'auto' | 'globe' | 'mercator',
    flyTo: options.get('flyTo')??undefined,
    bbox: options.get('bbox')??undefined,
    zoom: zoom?Number.parseFloat(zoom):undefined,
    year: year?Number.parseInt(year):undefined,

    writeToHash: function() {
        this.flyTo?options.set('flyTo', this.flyTo):options.delete('flyTo');
        this.bbox?options.set('bbox', this.bbox):options.delete('bbox');
        this.zoom?options.set('zoom', this.zoom.toString()):options.delete('zoom');
        this.year?options.set('year', this.year.toString()):options.delete('year');
        window.location.hash = '#'+options.toString();
    }
};