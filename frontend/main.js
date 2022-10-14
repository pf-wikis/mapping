import './style.scss';
import 'maplibre-gl/dist/maplibre-gl.css';
import * as maplibregl from "maplibre-gl";
import PureContextMenu from "pure-context-menu";

//<script src='https://unpkg.com/maplibre-gl@latest/dist/maplibre-gl.js'></script>
//<link href='https://unpkg.com/maplibre-gl@latest/dist/maplibre-gl.css' rel='stylesheet' />

function createLayer(name, base) {
  return Object.assign({
    'id': base.type+'_'+name,
    'source': 'golarion',
    'source-layer': name
  }, base);
}

function interpolateWithCamera(factor) {
  return [
    'interpolate',
    ['exponential', 2],
    ['zoom'],
    0, factor,
    22, factor*4194304,
  ]
}

let colors = {
  water:           'rgb(162, 203, 255)',
  waterDarker:     'rgb( 81, 101, 127)',
  land:            'rgb(240, 237, 229)',
  landDarker:      'rgb(207, 195, 160)',
  ice:             'rgb(240, 240, 254)',
  iceDarker:       'rgb(200, 200, 214)',
  swamp:           'rgb(200, 205, 179)',
  swampDarker:     'rgb(100, 105, 079)',
  forest:          'rgb(210, 228, 200)',
  forestDarker:    'rgb(110, 128, 100)',
  mountains:       'rgb(229, 221, 199)',
  mountainsDarker: 'rgb(129, 121, 099)',
  border:          'rgb(200, 200, 200)',
  borderDarker:    'rgb(100, 100, 100)',
  white:           'rgb(255, 255, 255)',
  black:           'rgb( 20,  20,  20)'
};

let layers = [
  {
    id: 'background',
    type: 'background',
    paint: {
      'background-color': colors.water,
    }
  },
  createLayer('continents', {
    type: 'fill',
    paint: {
      'fill-color': colors.land,
    }
  }),
  createLayer('ice_mass', {
    type: 'fill',
    paint: {
      'fill-color': colors.ice,
    }
  }),
  createLayer('swamp', {
    type: 'fill',
    paint: {
      'fill-color': colors.swamp,
    }
  }),
  createLayer('forests', {
    type: 'fill',
    paint: {
      'fill-color': colors.forest,
    }
  }),
  createLayer('mountains', {
    type: 'fill',
    paint: {
      'fill-color': colors.mountains,
    }
  }),
  createLayer('rivers', {
    type: 'line',
    paint: {
      'line-color': colors.water,
      'line-width': interpolateWithCamera(.05),
    },
    layout: {
      'line-cap': 'round'
    }
  }),
  createLayer('water_body', {
    type: 'fill',
    paint: {
      'fill-color': colors.water,
    }
  }),
  createLayer('rivers', {
    type: 'symbol',
    minzoom: 5,
    layout: {
      'symbol-placement': 'line',
      'text-max-angle': 20,
      'text-field': ['get', 'Name'],
      'text-font': ['Klokantech Noto Sans Regular'],
      'symbol-spacing': 300,
      'text-size': [
        'interpolate',
        ['linear'],
        ['zoom'],
         5,  2,
        10, 16,
      ],
    },
    paint: {
      'text-color': colors.water,
      'text-halo-color': colors.waterDarker,
      'text-halo-width': [
        'interpolate',
        ['linear'],
        ['zoom'],
         5, .125,
        10, 1,
      ],
    }
  }),
  createLayer('borders', {
    type: 'line',
    paint: {
      'line-color': colors.border,
      'line-width': [
        'interpolate',
        ['linear'],
        ['zoom'],
        1, .5,
        20, 6,
      ],
    }
  }),
  createLayer('ice_mass_label', {
    type: 'symbol',
    layout: {
      'text-field': ['get', 'Name'],
      'text-font': ['Klokantech Noto Sans Regular'],
      'text-overlap': 'always',
      'text-size': 32,
    },
    paint: {
      'text-color': colors.ice,
      'text-halo-color': colors.iceDarker,
      'text-halo-width': 1
    }
  }),
  createLayer('forests_label', {
    type: 'symbol',
    minzoom: 6,
    layout: {
      'text-field': ['get', 'Name'],
      'text-font': ['Klokantech Noto Sans Regular'],
      'text-size': 16,
    },
    paint: {
      'text-color': colors.forest,
      'text-halo-color': colors.forestDarker,
      'text-halo-width': 1
    }
  }),
  createLayer('mountains_label', {
    type: 'symbol',
    minzoom: 6,
    layout: {
      'text-field': ['get', 'Name'],
      'text-font': ['Klokantech Noto Sans Regular'],
      'text-size': 16,
    },
    paint: {
      'text-color': colors.mountains,
      'text-halo-color': colors.mountainsDarker,
      'text-halo-width': 1
    }
  }),
  createLayer('swamp_label', {
    type: 'symbol',
    minzoom: 6,
    layout: {
      'text-field': ['get', 'Name'],
      'text-font': ['Klokantech Noto Sans Regular'],
      'text-size': 16,
    },
    paint: {
      'text-color': colors.swamp,
      'text-halo-color': colors.swampDarker,
      'text-halo-width': 1
    }
  }),
  createLayer('water_body_label', {
    type: 'symbol',
    minzoom: 6,
    layout: {
      'text-field': ['get', 'Name'],
      'text-font': ['Klokantech Noto Sans Regular'],
      'text-size': 16,
    },
    paint: {
      'text-color': colors.water,
      'text-halo-color': colors.waterDarker,
      'text-halo-width': 1
    }
  }),
  createLayer('continents_label', {
    type: 'symbol',
    maxzoom: 2,
    layout: {
      'text-field': ['get', 'Name'],
      'text-font': ['Klokantech Noto Sans Regular'],
      'text-overlap': 'always',
      'text-size': interpolateWithCamera(10),
    },
    paint: {
      'text-color': colors.land,
      'text-halo-color': colors.landDarker,
      'text-halo-width': interpolateWithCamera(1)
    }
  }),
  createLayer('cities', {
    id: 'city-icons',
    type: 'symbol',
    maxzoom: 10,
    layout: {
      'icon-image': ['step',
        ['get', 'size'],
        'city-major',
        1, 'city-large',
        2, 'city-medium',
        3, 'city-small'
      ],
      'icon-pitch-alignment': 'map',
      'icon-allow-overlap': true,
      'icon-overlap': 'always',
      'icon-ignore-placement': true,
    },
    paint: {
    }
  }),
  createLayer('cities', {
    id: 'city-labels',
    type: 'symbol',
    maxzoom: 10,
    layout: {
      'text-field': ['get', 'Name'],
      'text-font': ['Klokantech Noto Sans Regular'],
      'text-size': ['step',
        ['get', 'size'],
        16,
        1, 14,
        2, 12,
        3, 10
      ],
      'text-variable-anchor': ["left", "right"],
      'symbol-sort-key': ['get', 'size'],
      'text-radial-offset': ['step',
        ['get', 'size'],
        .5,
        1, .4,
        2, .25,
        3, .2
      ],
    },
    paint: {
      'text-color': colors.white,
      'text-halo-color': colors.black,
      'text-halo-width': .8
    }
  }),
  createLayer('country_label', {
    type: 'symbol',
    minzoom: 2,
    maxzoom: 6,
    layout: {
      'text-field': ['get', 'Name'],
      'text-font': ['Klokantech Noto Sans Regular'],
      'text-size': 24,
    },
    paint: {
      'text-color': colors.white,
      'text-halo-color': colors.borderDarker,
      'text-halo-width': 1
    }
  }),
];

export const map = new maplibregl.Map({
  container: 'map-container',
  style: {
    version: 8,
    sources: {
      golarion: {
        type: 'vector',
        attribution: "Created under the Paizo Inc. Community Use Policy",
        tiles: [
          document.baseURI+'data/golarion/{z}/{x}/{y}.pbf.json'
        ],
        minzoom: 0,
        maxzoom: 7
      }
    },
    sprite: document.baseURI+'sprites/sprites',
    layers: layers,
    glyphs: document.baseURI+'fonts/{fontstack}/{range}.pbf.json'
  },
});
map.on('error', function(err) {
  console.log(err.error.message);
  document.getElementById("map-container").innerHTML = err.error.message;
});
map.addControl(new maplibregl.NavigationControl());
map.addControl(new maplibregl.ScaleControl({
  unit: 'imperial'
}));
map.addControl(new maplibregl.ScaleControl({
  unit: 'metric'
}));

var latLong = null;
map.on('contextmenu', function(e) {
  latLong = e.lngLat.wrap();
});





////////////////////////////////////////// make cities clickable
function showPointer() {
  map.getCanvas().style.cursor = 'pointer';
}

function hidePointer() {
  map.getCanvas().style.cursor = '';
}

map.on('mouseenter', 'city-icons', showPointer);
map.on('mouseenter', 'city-labels', showPointer);

map.on('mouseleave', 'city-icons', hidePointer);
map.on('mouseleave', 'city-labels', hidePointer);

function clickOnCity(e) {
  let coordinates = e.features[0].geometry.coordinates.slice();
  let props = e.features[0].properties;
   
  // Ensure that if the map is zoomed out such that multiple
  // copies of the feature are visible, the popup appears
  // over the copy being pointed to.
  while (Math.abs(e.lngLat.lng - coordinates[0]) > 180) {
    coordinates[0] += e.lngLat.lng > coordinates[0] ? 360 : -360;
  }
   
  new maplibregl.Popup()
    .setLngLat(coordinates)
    .setHTML('<a href="'+props.link+'" target="_blank">'+props.Name+"</a>")
    .addTo(map);
  e.stopPropagation();
}

map.on('click', 'city-icons', clickOnCity);
map.on('click', 'city-labels', clickOnCity);





///////////////////////////////////////// right click menu
const items = [
  {
    label: "Copy Lat/Long",
    callback: (e) => {
      let text = latLong.lat+", "+latLong.lng;

      if (!navigator.clipboard) {
        alert(text);
        return;
      }
      navigator.clipboard.writeText(text).then(function() {
        console.log(`Copied '${text}' into clipboard`);
      }, function(err) {
        alert(text);
      });
    },
  },
];
const menu = new PureContextMenu(document.getElementById("map-container"), items, {
  show: (e) => {
    //only show if map itself is clicked
    return e.target.classList.contains('maplibregl-canvas');
  }
});






//////////debugging options
/*
map.showCollisionBoxes = true;
const interval = setInterval(function() {
  console.log(map.getZoom());
}, 5000);
*/