import './style.scss';
import 'maplibre-gl/dist/maplibre-gl.css';
import * as maplibre from "maplibre-gl";
import PureContextMenu from "pure-context-menu";


//constants
const limit = {
  districts:    11,
  river_labels:  5
};

//check if running embedded
var urlParams = new URLSearchParams(window.location.hash.replace("#","?"));
const embedded = (urlParams.get('embedded') === 'true');
const mapContainer = document.getElementById("map-container");

if(embedded) {
  mapContainer.classList.add("embedded");
}


function createLayer(name, base) {
  return Object.assign({
    'id': base.type+'_'+name,
    'source': 'golarion',
    'source-layer': name
  }, base);
}

function interpolateTextWithCamera(factor) {
  return [
    'interpolate',
    ['exponential', 2],
    ['zoom'],
    0, factor,
    22, factor*4194304,
  ]
}

function interpolateWithCamera(base) {
  return [
    'interpolate',
    ['exponential', 2],
    ['zoom'],
     0, ['*', base, 0.00001],
    22, ['*', base, 41.94304],
  ]
}

function getOrDefault(field, defaultValue) {
  return ['case', ['has', field], ['get', 'field'], defaultValue];
}

let colors = {
  water:           'rgb(121, 176, 255)',
  waterDarker:     'rgb( 99, 144, 209)',
  land:            'rgb(192, 253, 160)',
  landDarker:      'rgb(157, 207, 131)',
  districts:       'rgb(220, 220, 210)',
  districtsDarker: 'rgb(067, 065, 060)',
  deserts:         'rgb(255, 237, 155)',
  desertsDarker:   'rgb(210, 195, 128)',
  ice:             'rgb(240, 240, 254)',
  iceDarker:       'rgb(200, 200, 214)',
  swamp:           'rgb(178, 194, 117)',
  swampDarker:     'rgb(146, 159,  96)',
  forest:          'rgb(153, 221, 117)',
  forestDarker:    'rgb(125, 181,  96)',
  hills:           'rgb(219, 211, 183)',
  hillsDarker:     'rgb(179, 173, 150)',
  mountains:       'rgb(189, 182, 147)',
  mountainsDarker: 'rgb(155, 149, 120)',
  border:          'rgb(200, 200, 200)',
  borderDarker:    'rgb(100, 100, 100)',
  walls:           'rgb(140, 137, 129)',
  chasms:          'rgb( 59,  51,  29)',
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
  createLayer('districts', {
    type: 'fill',
    paint: {
      'fill-color': colors.districts,
    }
  }),
  createLayer('districts_borders', {
    type: 'line',
    paint: {
      'line-color': colors.land,
      'line-width': interpolateWithCamera(200),
    },
  }),
  createLayer('chasms', {
    type: 'fill',
    paint: {
      'fill-color': colors.chasms,
    }
  }),
  createLayer('ice', {
    type: 'fill',
    paint: {
      'fill-color': colors.ice,
    }
  }),
  createLayer('swamps', {
    type: 'fill',
    paint: {
      'fill-color': colors.swamp,
    }
  }),
  createLayer('deserts', {
    type: 'fill',
    paint: {
      'fill-color': colors.deserts,
    }
  }),
  createLayer('forests', {
    type: 'fill',
    paint: {
      'fill-color': colors.forest,
    }
  }),
  createLayer('hills', {
    type: 'fill',
    paint: {
      'fill-color': colors.hills,
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
      'line-width': interpolateWithCamera(['case', ['has', 'width'], ['get', 'width'], 2000]),
    },
    layout: {
      'line-cap': 'round'
    }
  }),
  createLayer('waters', {
    type: 'fill',
    paint: {
      'fill-color': colors.water,
    }
  }),
  createLayer('rivers', {
    type: 'symbol',
    minzoom: limit.river_labels,
    layout: {
      'symbol-placement': 'line',
      'text-max-angle': 20,
      'text-field': ['get', 'Name'],
      'text-font': ['NotoSans-Medium'],
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
  createLayer('walls', {
    type: 'fill',
    paint: {
      'fill-color': colors.walls,
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
  createLayer('ice_label', {
    type: 'symbol',
    layout: {
      'text-field': ['get', 'Name'],
      'text-font': ['NotoSans-Medium'],
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
      'text-font': ['NotoSans-Medium'],
      'text-size': 16,
    },
    paint: {
      'text-color': colors.forest,
      'text-halo-color': colors.forestDarker,
      'text-halo-width': 1
    }
  }),
  createLayer('hills_label', {
    type: 'symbol',
    minzoom: 6,
    layout: {
      'text-field': ['get', 'Name'],
      'text-font': ['NotoSans-Medium'],
      'text-size': 16,
    },
    paint: {
      'text-color': colors.hills,
      'text-halo-color': colors.hillsDarker,
      'text-halo-width': 1
    }
  }),
  createLayer('mountains_label', {
    type: 'symbol',
    minzoom: 6,
    layout: {
      'text-field': ['get', 'Name'],
      'text-font': ['NotoSans-Medium'],
      'text-size': 16,
    },
    paint: {
      'text-color': colors.mountains,
      'text-halo-color': colors.mountainsDarker,
      'text-halo-width': 1
    }
  }),
  createLayer('deserts_label', {
    type: 'symbol',
    minzoom: 6,
    layout: {
      'text-field': ['get', 'Name'],
      'text-font': ['NotoSans-Medium'],
      'text-size': 16,
    },
    paint: {
      'text-color': colors.deserts,
      'text-halo-color': colors.desertsDarker,
      'text-halo-width': 1
    }
  }),
  createLayer('swamps_label', {
    type: 'symbol',
    minzoom: 6,
    layout: {
      'text-field': ['get', 'Name'],
      'text-font': ['NotoSans-Medium'],
      'text-size': 16,
    },
    paint: {
      'text-color': colors.swamp,
      'text-halo-color': colors.swampDarker,
      'text-halo-width': 1
    }
  }),
  createLayer('waters_label', {
    type: 'symbol',
    minzoom: 6,
    layout: {
      'text-field': ['get', 'Name'],
      'text-font': ['NotoSans-Medium'],
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
      'text-font': ['NotoSans-Medium'],
      'text-overlap': 'always',
      'text-size': interpolateTextWithCamera(10),
    },
    paint: {
      'text-color': colors.land,
      'text-halo-color': colors.landDarker,
      'text-halo-width': interpolateTextWithCamera(1)
    }
  }),
  createLayer('locations', {
    id: 'location-icons',
    type: 'symbol',
    maxzoom: 15,
    layout: {
      'icon-image': ['match', ['get', 'type'],
        'tower', 'location-tower',
        'location-other'
      ],
      'icon-pitch-alignment': 'map',
      'icon-allow-overlap': true,
      'icon-overlap': 'always',
      'icon-ignore-placement': true,
    },
    paint: {
    }
  }),
  createLayer('locations', {
    id: 'location-labels',
    type: 'symbol',
    maxzoom: 15,
    layout: {
      'text-field': ['get', 'Name'],
      'text-font': ['NotoSans-Medium'],
      'text-size': 12,
      'text-variable-anchor': ["left", "right"],
      'symbol-sort-key': ['get', 'size'],
      'text-radial-offset': .3,
    },
    paint: {
      'text-color': colors.white,
      'text-halo-color': colors.black,
      'text-halo-width': .8
    }
  }),
  createLayer('cities', {
    id: 'city-icons',
    type: 'symbol',
    maxzoom: limit.districts,
    layout: {
      'icon-image': ['case',
        ['get', 'capital'], ['step',
          ['get', 'size'],
          'city-major-capital',
          1, 'city-large-capital',
          2, 'city-medium-capital',
          3, 'city-small-capital'
        ], ['step',
          ['get', 'size'],
          'city-major',
          1, 'city-large',
          2, 'city-medium',
          3, 'city-small'
        ]
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
    maxzoom: limit.districts,
    layout: {
      'text-field': ['get', 'Name'],
      'text-font': ['NotoSans-Medium'],
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
  createLayer('districts_label', {
    type: 'symbol',
    minzoom: limit.districts,
    layout: {
      'text-field': ['get', 'Name'],
      'text-font': ['NotoSans-Medium'],
      'text-size': ['step',
        ['get', 'size'],
        16,
        1, 14,
        2, 12,
        3, 10
      ],
      'text-anchor': 'center',
    },
    paint: {
      'text-color': colors.white,
      'text-halo-color': colors.black,
      'text-halo-width': .8
    }
  }),
  createLayer('countries_label', {
    type: 'symbol',
    minzoom: 2,
    maxzoom: 6,
    layout: {
      'text-field': ['get', 'Name'],
      'text-font': ['NotoSans-Medium'],
      'text-size': 24,
    },
    paint: {
      'text-color': colors.white,
      'text-halo-color': colors.borderDarker,
      'text-halo-width': 1
    }
  }),
];

var root = location.origin + location.pathname
if(!root.endsWith("/")) root += "/";
const maxZoom = parseInt(import.meta.env.VITE_MAX_ZOOM);
const dataPath = import.meta.env.VITE_DATA_PATH;

export const map = new maplibre.Map({
  container: 'map-container',
  hash: 'location',
  attributionControl: false,
  style: {
    version: 8,
    sources: {
      golarion: {
        type: 'vector',
        attribution: '<a href="https://paizo.com/community/communityuse">Paizo CUP</a>',
        tiles: [
          root+dataPath+'/golarion/{z}/{x}/{y}.pbf.json'
        ],
        minzoom: 0,
        maxzoom: maxZoom
      }
    },
    sprite: root+'sprites/sprites',
    layers: layers,
    glyphs: root+'fonts/{fontstack}/{range}.pbf.json'
  },
});
map.on('error', function(err) {
  console.log(err.error.message);
  document.getElementById("map-container").innerHTML = err.error.message;
});
if(!embedded) {
  map.addControl(new maplibre.NavigationControl());
}
map.addControl(new maplibre.ScaleControl({
  unit: 'imperial',
  maxWidth: embedded?50:100,
}));
map.addControl(new maplibre.ScaleControl({
  unit: 'metric',
  maxWidth: embedded?50:100,
}));
map.addControl(new maplibre.AttributionControl({
  compact: embedded
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
map.on('mouseenter', 'location-icons', showPointer);
map.on('mouseenter', 'location-labels', showPointer);
map.on('mouseleave', 'location-icons', hidePointer);
map.on('mouseleave', 'location-labels', hidePointer);


const popup = new maplibre.Popup();
function clickOnWikilink(e) {
  let coordinates = e.features[0].geometry.coordinates.slice();
  let props = e.features[0].properties;

  //if this feature has multiple geometry use the closest one
  if(Array.isArray(coordinates[0])) {
    coordinates = coordinates.reduce((prev, curr) => {
      if(prev === undefined) {
        return curr;
      }
      let prevDist = e.lngLat.distanceTo(new maplibre.LngLat(...prev));
      let currDist = e.lngLat.distanceTo(new maplibre.LngLat(...curr));
      return prevDist < currDist ? prev : curr;
    });
  }
   
  // Ensure that if the map is zoomed out such that multiple
  // copies of the feature are visible, the popup appears
  // over the copy being pointed to.
  while (Math.abs(e.lngLat.lng - coordinates[0]) > 180) {
    coordinates[0] += e.lngLat.lng > coordinates[0] ? 360 : -360;
  }
   
  popup
    .setLngLat(coordinates)
    .setHTML('<a href="'+props.link+'" target="_blank">'+props.Name+"</a>")
    .addTo(map);
}

map.on('click', 'city-icons',  clickOnWikilink);
map.on('click', 'city-labels', clickOnWikilink);
map.on('click', 'location-icons',  clickOnWikilink);
map.on('click', 'location-labels', clickOnWikilink);





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
const menu = new PureContextMenu(mapContainer, items, {
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