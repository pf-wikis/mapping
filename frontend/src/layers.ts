import { DataDrivenPropertyValueSpecification, ExpressionSpecification, LayerSpecification } from "maplibre-gl";

let limit = {
  districts:    11
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
  black:           'rgb( 10,  10,  10)'
};

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
    0, factor,
    22, factor*(2**22),
  ]
}

function blendInOut(from:number, to:number):ExpressionSpecification {
  return ['interpolate', ['linear'], ['zoom'],
    from, 0,
    from+.5, 1,
    to  -.5, 1,
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
      'fill-opacity': .8,
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
      'fill-color': 'rgb(140, 137, 129)',
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
    maxzoom: 6,
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
    minzoom: 2,
    maxzoom: 4,
    paint: {
      'line-color': colors.regionBorders,
      'line-width': 2,
      'line-opacity': blendInOut(2,4)
    },
    layout: {
      'line-cap': 'round'
    }
  }),
  createLayer('river-labels', {
    type: 'symbol',
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
  createLayer('ice-labels', {
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
  createLayer('forest-labels', {
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
  createLayer('special-labels', {
    type: 'symbol',
    layout: {
      'text-field': ['get', 'Name'],
      'text-font': ['NotoSans-Medium'],
      'text-size': 16,
    },
    paint: {
      'text-color': colors.districts,
      'text-halo-color': colors.districtsDarker,
      'text-halo-width': 1
    }
  }),
  createLayer('labels', {
    type: 'symbol',
    layout: {
      'text-field': ['get', 'Name'],
      'text-font': ['NotoSans-Medium'],
      'text-size': 16,
    },
    paint: {
      'text-color': colors.districts,
      'text-halo-color': colors.districtsDarker,
      'text-halo-width': 1
    }
  }),
  createLayer('hill-labels', {
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
  createLayer('mountain-labels', {
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
  createLayer('desert-labels', {
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
  createLayer('swamp-labels', {
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
  createLayer('water-labels', {
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
      'text-field': ['get', 'Name'],
      'text-font': ['NotoSans-Medium'],
      'text-size': 14,
      'text-variable-anchor': ["left", "right"],
      'text-radial-offset': .5,
    },
    paint: {
      'text-color': colors.white,
      'text-halo-color': colors.black,
      'text-halo-width': .8
    }
  }),
  createLayer('district-labels', {
    type: 'symbol',
    minzoom: limit.districts,
    layout: {
      'text-field': ['get', 'Name'],
      'text-font': ['NotoSans-Medium'],
      'text-size': 16,
      'text-variable-anchor': ['center','top','bottom'],
      'symbol-z-order': 'source',
    },
    paint: {
      'text-color': colors.white,
      'text-halo-color': colors.black,
      'text-halo-width': 1
    }
    
  }),
  createLayer('province-labels', {
    minzoom: 4,
    maxzoom: 7,
    type: 'symbol',
    layout: {
      'text-field': ['get', 'Name'],
      'text-font': ['NotoSans-Medium'],
      'text-size': ['interpolate', ['linear'], ['zoom'],
        5, 5,
        7, 20,
      ],
      'text-variable-anchor': ['center','top','bottom'],
      'symbol-z-order': 'source',
    },
    paint: {
      'text-color': colors.white,
      'text-halo-color': colors.regionNames,
      'text-halo-width': ['interpolate', ['linear'], ['zoom'],
        5, .375,
        7, 1.5,
      ],
    }
  }),
  createLayer('nation-labels', {
    minzoom: 3,
    maxzoom: 6,
    type: 'symbol',
    filter: ['any',
      ['!', ['get', 'inSubregion']],
      ['>', ['zoom'], 4]
    ],
    layout: {
      'text-field': ['get', 'Name'],
      'text-font': ['NotoSans-Medium'],
      'text-size': ['interpolate', ['linear'], ['zoom'],
        4, 10,
        5, 25,
      ],
      'text-variable-anchor': ['center','top','bottom'],
      'symbol-z-order': 'source',
    },
    paint: {
      'text-color': colors.white,
      'text-halo-color': colors.regionNames,
      'text-halo-width': ['interpolate', ['linear'], ['zoom'],
        4, .75,
        5, 1.875,
      ],
    }
  }),
  createLayer('subregion-labels', {
    minzoom: 3,
    maxzoom: 5,
    type: 'symbol',
    layout: {
      'text-field': ['get', 'Name'],
      'text-font': ['NotoSans-Medium'],
      'text-size': ['interpolate', ['linear'], ['zoom'],
        4, 10,
        5, 25,
      ],
      'text-variable-anchor': ['center','top','bottom'],
      'symbol-z-order': 'source',
    },
    paint: {
      'text-color': colors.white,
      'text-halo-color': colors.regionNames,
      'text-halo-width': ['interpolate', ['linear'], ['zoom'],
        4, .75,
        5, 1.875,
      ],
    }
  }),
  createLayer('region-labels', {
    minzoom: 1,
    maxzoom: 4,
    type: 'symbol',
    layout: {
      'text-field': ['get', 'Name'],
      'text-font': ['NotoSans-Medium'],
      'text-size': 20,
      'text-variable-anchor': ['center','top','bottom'],
      'symbol-z-order': 'source',
    },
    paint: {
      'text-color': colors.regionNames,
      'text-halo-color': colors.regionNamesOut,
      'text-halo-width': 1.5,
    }
  }),
  createLayer('continent-labels', {
    type: 'symbol',
    maxzoom: 2,
    layout: {
      'text-field': ['get', 'Name'],
      'text-font': ['NotoSans-Medium'],
      'text-overlap': 'always',
      'text-ignore-placement': true,
      'text-size': interpolateTextWithCamera(10),
      'symbol-z-order': 'source',
    },
    paint: {
      'text-color': colors.land,
      'text-halo-color': colors.landDarker,
      'text-halo-width': interpolateTextWithCamera(1)
    }
  }),
];

export default layers;
