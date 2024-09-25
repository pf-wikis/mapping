import { DataDrivenPropertyValueSpecification, ExpressionSpecification, LayerSpecification } from "maplibre-gl";

let limit = {
  districts:    18
};

let colors = {
  water:           'rgb(138, 180, 248)',
  waterDeep:       'rgb(110, 160, 245)',
  waterDarker:     'rgb(  9,  64, 153)',
  land:            'rgb(248, 241, 225)',
  landDarker:      'rgb(162, 124,  38)',
  districts:       'rgb(212, 204, 185)',
  districtsDarker: 'rgb(104,  92,  64)',
  deserts:         'rgb(255, 247, 190)',
  desertsDarker:   'rgb(188, 164,   0)',
  ice:             'rgb(255, 255, 255)',
  iceDarker:       'rgb(108, 108, 108)',
  swamp:           'rgb(183, 197, 188)',
  swampDarker:     'rgb( 72,  88,  78)',
  forest:          'rgb(187, 226, 198)',
  forestDarker:    'rgb( 52, 122,  72)',
  hills:           'rgb(235, 227, 205)',
  hillsDarker:     'rgb(132, 111,  53)',
  mountains:       'rgb(222, 212, 184)',
  mountainsDarker: 'rgb(117, 100,  54)',
  regionBorders:   'rgb(107,  42,  33)',
  regionNames:     'rgb( 17,  42,  97)',
  regionNamesOut:  'rgb(213, 195, 138)',
  nationBorders:   'rgb(170, 170, 170)',
  borderDarker:    'rgb( 74,  74,  74)',
  white:           'rgb(255, 255, 255)',
  whiteHalo:       'rgba(255, 255, 255, 0.8)',
  red:             'rgb(255,   0,   0)',
  black:           'rgb( 25,  25,  25)'
};

//scale font larger for lower dpr displays
const fs = window.devicePixelRatio===1?2:1;
const base_font_size = 8*fs;
const sans_font = 'AlegreyaSans-Regular';
const serif_font = 'Alegreya-Regular';

const props = {
  filterMinzoom: ["get", "filterMinzoom"] as ExpressionSpecification,
  filterMaxzoom: ["get", "filterMaxzoom"] as ExpressionSpecification
}

const equatorMeter2Deg = 1/111319.491 * 1.5; //no idea where this second factor comes from -.-
function interpolateWithCamera(base:ExpressionSpecification):DataDrivenPropertyValueSpecification<number> {
  return [
    'interpolate',
    ['exponential', 2],
    ['zoom'],
     0, ['*', base, equatorMeter2Deg],
    22, ['*', base, equatorMeter2Deg*(2**22)],
  ] as ExpressionSpecification
}

function interpolateTextWithCamera(factor:number):ExpressionSpecification {
  return [
    'interpolate',
    ['exponential', 2],
    ['zoom'],
    0, factor*fs,
    22, factor*(2**22)*fs,
  ]
}

function blendInOut(from:number, to:number):ExpressionSpecification {
  return ['interpolate', ['linear'], ['zoom'],
    from, 0,
    from+.5, .5,
    to  -.5, .5,
    to, 0
  ]
}

function createLayer(name:string, base:Partial<LayerSpecification>):LayerSpecification {
  return Object.assign({
    id: base.type+'_'+name,
    source: 'golarion',
    'source-layer': name,
    filter: ['all',
      ['any', ['!', ['has', 'filterMinzoom']], ['>=', ["zoom"], props.filterMinzoom]],
      ['any', ['!', ['has', 'filterMaxzoom']], ['<=', ["zoom"], props.filterMaxzoom]]
    ],
  }, base) as LayerSpecification;
}

let layers:LayerSpecification[] = [
  {
    id: 'background',
    type: 'background',
    paint: {
      'background-color': colors.waterDeep,
    }
  },
  createLayer('deep-waters', {
    type: 'fill',
    paint: {
      'fill-color': colors.water,
    }
  }),
  createLayer('land', {
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
  createLayer('ice', {
    type: 'fill',
    paint: {
      'fill-color': colors.ice,
      'fill-opacity': .9,
    }
  }),
  createLayer('forests', {
    type: 'fill',
    paint: {
      'fill-color': colors.forest,
    }
  }),
  createLayer('specials', {
    type: 'fill',
    paint: {
      'fill-color': ['get', 'color'],
    }
  }),
  createLayer('waters', {
    type: 'fill',
    paint: {
      'fill-color': colors.water,
    }
  }),
  createLayer('rivers', {
    type: 'fill',
    paint: {
      'fill-color': colors.water
    }
  }),
  createLayer('buildings', {
    type: 'fill',
    paint: {
      'fill-color': ["to-color", ['match', ['get', 'type'],
        'fortification', 'rgb(105, 105, 105)',
        'bridge', 'rgb(169, 169, 169)',
        'rgb(119, 136, 153)'
      ]]
    }
  }),
  createLayer('province-borders', {
    type: 'line',
    minzoom: 3,
    paint: {
      'line-color': colors.nationBorders,
      'line-width': 1,
      'line-opacity': blendInOut(3,99)
    },
    layout: {
      'line-cap': 'round'
    }
  }),
  createLayer('nation-borders', {
    type: 'line',
    paint: {
      'line-color': colors.nationBorders,
      'line-width': ["interpolate", ["exponential", 2], ["zoom"],
        3, .375,
        6, 3,
      ],
    },
    layout: {
      'line-cap': 'round'
    }
  }),
  createLayer('subregion-borders', {
    type: 'line',
    maxzoom: 8,
    paint: {
      'line-color': colors.nationBorders,
      'line-width': ["interpolate", ["exponential", 2], ["zoom"],
        0, .375,
        3, 3,
      ],
    },
    layout: {
      'line-cap': 'round'
    }
  }),
  createLayer('region-borders', {
    type: 'line',
    minzoom: 1,
    maxzoom: 6,
    paint: {
      'line-color': colors.regionBorders,
      'line-width': 2,
      'line-opacity': blendInOut(1,6)
    },
    layout: {
      'line-cap': 'round'
    }
  }),
  createLayer('river-labels', {
    type: 'symbol',
    layout: {
      'symbol-placement': 'line',
      'text-max-angle': 40,
      'text-field': ['upcase', ['get', 'Name']],
      'text-font': ['Alegreya-Regular'],
      'text-letter-spacing': 0.1,
      'symbol-spacing': [
        'interpolate',
        ['linear'],
        ['zoom'],
        1, 1,
        6, 250,
        12, 400,
      ],
      'text-size': [
        'interpolate',
        ['linear'],
        ['zoom'],
        1, base_font_size - (base_font_size / 2),
        6, base_font_size - (base_font_size / 4),
        10, base_font_size,
      ],
    },
    paint: {
      'text-color': colors.waterDarker,
      'text-halo-color': colors.water,
      'text-halo-width': [
        'interpolate',
        ['linear'],
        ['zoom'],
        5, .125,
        10, .5,
      ],
    }
  }),
  createLayer('ice-labels', {
    type: 'symbol',
    layout: {
      'text-field': ['upcase', ['get', 'Name']],
      'text-font': ['Alegreya-Regular'],
      'text-overlap': 'always',
      'text-size': base_font_size,
      'text-letter-spacing': 0.25,
    },
    paint: {
      'text-color': colors.iceDarker,
      'text-halo-color': colors.ice,
      'text-halo-width': .5
    }
  }),
  createLayer('forest-labels', {
    type: 'symbol',
    minzoom: 6,
    layout: {
      'text-field': ['upcase', ['get', 'Name']],
      'text-font': ['Alegreya-Regular'],
      'text-size': base_font_size,
      'text-letter-spacing': 0.25,
    },
    paint: {
      'text-color': colors.forestDarker,
      'text-halo-color': colors.forest,
      'text-halo-width': .5
    }
  }),
  createLayer('special-labels', {
    type: 'symbol',
    layout: {
      'text-field': ['upcase', ['get', 'Name']],
      'text-font': ['Alegreya-Regular'],
      'text-size': base_font_size,
      'text-letter-spacing': 0.25,
    },
    paint: {
      'text-color': colors.districtsDarker,
      'text-halo-color': colors.districts,
      'text-halo-width': .5
    }
  }),
  createLayer('labels', {
    type: 'symbol',
    layout: {
      'text-field': ['upcase', ['get', 'Name']],
      'text-font': ['Alegreya-Regular'],
      'text-size': base_font_size + (base_font_size / 3),
      'text-letter-spacing': 0.25,
    },
    paint: {
      'text-color': ['match', ['get', 'type'],
        'land', colors.districtsDarker,
        'waters', colors.waterDarker,
        colors.districtsDarker,
      ],
      'text-halo-color': ['match', ['get', 'type'],
        'land', colors.districts,
        'waters', colors.water,
        colors.districts,
      ],
      'text-halo-width': .5
    }
  }),
  createLayer('hill-labels', {
    type: 'symbol',
    minzoom: 6,
    layout: {
      'text-field': ['upcase', ['get', 'Name']],
      'text-font': ['Alegreya-Regular'],
      'text-size': base_font_size,
      'text-letter-spacing': 0.25,
    },
    paint: {
      'text-color': colors.hillsDarker,
      'text-halo-color': colors.hills,
      'text-halo-width': .5
    }
  }),
  createLayer('mountain-labels', {
    type: 'symbol',
    minzoom: 6,
    layout: {
      'text-field': ['upcase', ['get', 'Name']],
      'text-font': ['Alegreya-Regular'],
      'text-size': base_font_size,
      'text-letter-spacing': 0.25,
    },
    paint: {
      'text-color': colors.mountainsDarker,
      'text-halo-color': colors.mountains,
      'text-halo-width': .5
    }
  }),
  createLayer('desert-labels', {
    type: 'symbol',
    minzoom: 6,
    layout: {
      'text-field': ['upcase', ['get', 'Name']],
      'text-font': ['Alegreya-Regular'],
      'text-size': base_font_size,
      'text-letter-spacing': 0.25,
    },
    paint: {
      'text-color': colors.desertsDarker,
      'text-halo-color': colors.deserts,
      'text-halo-width': .5
    }
  }),
  createLayer('swamp-labels', {
    type: 'symbol',
    minzoom: 6,
    layout: {
      'text-field': ['upcase', ['get', 'Name']],
      'text-font': ['Alegreya-Regular'],
      'text-size': base_font_size,
      'text-letter-spacing': 0.25,
    },
    paint: {
      'text-color': colors.swampDarker,
      'text-halo-color': colors.swamp,
      'text-halo-width': .5
    }
  }),
  createLayer('water-labels', {
    type: 'symbol',
    minzoom: 6,
    layout: {
      'text-field': ['upcase', ['get', 'Name']],
      'text-font': ['Alegreya-Regular'],
      'text-size': base_font_size,
      'text-letter-spacing': 0.25,
    },
    paint: {
      'text-color': colors.waterDarker,
      'text-halo-color': colors.water,
      'text-halo-width': .5
    }
  }),

  createLayer('locations', {
    id: 'location-icons',
    type: 'symbol',
    layout: {
      'icon-image': ['get', 'icon'],
      'icon-pitch-alignment': 'map',
      'icon-overlap': 'always',
      'icon-ignore-placement': true,
      'icon-size': ["interpolate", ["exponential", 2], ["zoom"],
         0,            ["^", 2, ["-", -3, props.filterMinzoom]],
         1,            ["^", 2, ["-", -2, props.filterMinzoom]],
         2, ["min", 1, ["^", 2, ["-", -1, props.filterMinzoom]]],
         3, ["min", 1, ["^", 2, ["-",  0, props.filterMinzoom]]],
         4, ["min", 1, ["^", 2, ["-",  1, props.filterMinzoom]]],
         5, ["min", 1, ["^", 2, ["-",  2, props.filterMinzoom]]],
         6, ["min", 1, ["^", 2, ["-",  3, props.filterMinzoom]]],
         7, ["min", 1, ["^", 2, ["-",  4, props.filterMinzoom]]],
         8, ["min", 1, ["^", 2, ["-",  5, props.filterMinzoom]]],
         9, ["min", 1, ["^", 2, ["-",  6, props.filterMinzoom]]],
        10, ["min", 1, ["^", 2, ["-",  7, props.filterMinzoom]]],
      ] as any
    },
    paint: {
    }
  }),
  createLayer('locations', {
    id: 'location-labels',
    type: 'symbol',
    filter: ['all',
      ['>', ["zoom"], ["+", props.filterMinzoom, 3]],
      ['any', ['!', ['has', 'filterMaxzoom']], ['<=', ["zoom"], props.filterMaxzoom]]
    ],
    layout: {
      'text-field': ['match', ['get', 'icon'],
        'city-major-capital', ['upcase', ['get', 'Name']],
        ['get', 'Name'],
      ],
      'text-font': ['AlegreyaSans-Regular'],
      'text-size': base_font_size,
      'text-variable-anchor': ["left", "right"],
      'text-radial-offset': .5
    },
    paint: {
      'text-color': colors.black,
      'text-halo-color': colors.whiteHalo,
      'text-halo-width': .875
    }
  }),
  /* This seems to have no effect, and settlements appear to use the locations layer instead
  createLayer('cities', {
    id: 'city-labels',
    type: 'symbol',
    maxzoom: limit.districts,
    filter: ['>', ["-", ["zoom"], props.filterMinzoom], 3],
    layout: {
      'text-field': ['upcase', ['get', 'Name']],
      'text-font': ['AlegreyaSans-Regular'],
      'text-size': ['step',
        ['get', 'size'],
        base_font_size + (base_font_size / 6),
        1, base_font_size,
        2, base_font_size - (base_font_size / 6),
        3, base_font_size - (base_font_size / 3)
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
      'text-color': colors.black,
      'text-halo-color': colors.white,
      'text-halo-width': .5
    }
  }),*/
  createLayer('district-labels', {
    type: 'symbol',
    minzoom: limit.districts,
    layout: {
      'text-field': ['upcase', ['get', 'Name']],
      'text-font': ['AlegreyaSans-Regular'],
      'text-size': base_font_size,
      'text-variable-anchor': ['center','top','bottom'],
      'symbol-z-order': 'source',
    },
    paint: {
      'text-color': colors.black,
      'text-halo-color': colors.whiteHalo,
      'text-halo-width': .875,
    }
  }),
  createLayer('province-labels', {
    minzoom: 5,
    maxzoom: 7,
    type: 'symbol',
    layout: {
      'text-field': ['upcase', ['get', 'Name']],
      'text-font': ['AlegreyaSans-Regular'],
      'text-size': ['interpolate', ['linear'], ['zoom'],
        5, base_font_size - (base_font_size / 6),
        7, base_font_size + (base_font_size / 6),
      ],
      'text-letter-spacing': 0.1,
      'text-variable-anchor': ['center','top','bottom'],
      'symbol-z-order': 'source',
    },
    paint: {
      'text-color': colors.black,
      'text-halo-color': colors.whiteHalo,
      'text-halo-width': ['interpolate', ['linear'], ['zoom'],
        5, .5,
        7, .875,
      ],
    }
  }),
  createLayer('nation-labels', {
    minzoom: 3,
    maxzoom: 8,
    type: 'symbol',
    filter: ['any',
      ['!', ['get', 'inSubregion']],
      ['>', ['zoom'], 4]
    ],
    layout: {
      'text-field': ['upcase', ['get', 'Name']],
      'text-font': ['AlegreyaSans-Regular'],
      'text-size': ['interpolate', ['linear'], ['zoom'],
        3, base_font_size - (base_font_size / 4),
        6, base_font_size + (base_font_size / 2),
      ],
      'text-letter-spacing': 0.1,
      'text-variable-anchor': ['center','top','bottom'],
      'symbol-z-order': 'source',
    },
    paint: {
      'text-color': colors.black,
      'text-halo-color': colors.whiteHalo,
      'text-halo-width': ['interpolate', ['linear'], ['zoom'],
        4, 2,
        5, .875,
      ],
    }
  }),
  createLayer('subregion-labels', {
    minzoom: 6,
    maxzoom: 8,
    type: 'symbol',
    layout: {
      'text-field': ['upcase', ['get', 'Name']],
      'text-font': ['AlegreyaSans-Regular'],
      'text-size': ['interpolate', ['linear'], ['zoom'],
        4, base_font_size,
        5, base_font_size + (base_font_size / 2),
      ],
      'text-variable-anchor': ['center','top','bottom'],
      'symbol-z-order': 'source',
    },
    paint: {
      'text-color': colors.black,
      'text-halo-color': colors.whiteHalo,
      'text-halo-width': ['interpolate', ['linear'], ['zoom'],
        4, 2,
        5, .875,
      ],
    }
  }),
  createLayer('region-labels', {
    minzoom: 1,
    maxzoom: 4,
    type: 'symbol',
    layout: {
      'text-field': ['upcase', ['get', 'Name']],
      'text-font': ['Alegreya-Regular'],
      'text-size': base_font_size + (base_font_size / 3),
      'text-letter-spacing': 0.1,
      'text-variable-anchor': ['center','top','bottom'],
      'symbol-z-order': 'source',
    },
    paint: {
      'text-color': colors.regionNames,
      'text-halo-color': colors.regionNamesOut,
      'text-halo-width': .875,
    }
  }),
  createLayer('continent-labels', {
    type: 'symbol',
    maxzoom: 2,
    layout: {
      'text-field': ['get', 'Name'],
      'text-font': ['AlegreyaSans-Regular'],
      'text-overlap': 'always',
      'text-ignore-placement': true,
      'text-size': interpolateTextWithCamera(10),
      'symbol-z-order': 'source',
    },
    paint: {
      'text-color': colors.landDarker,
      'text-halo-color': colors.land,
      'text-halo-width': interpolateTextWithCamera(.25)
    }
  }),
];

export default layers;
