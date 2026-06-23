import { DataDrivenPropertyValueSpecification, ExpressionSpecification, FillLayerSpecification, FilterSpecification, LayerSpecification, LineLayerSpecification, SymbolLayerSpecification, StyleSpecification } from "maplibre-gl";
import timeMeta from "../utils/timeMeta";
import { defineConfig, loadEnv } from 'vite';
import {ExistingLayer, Prop, propsMeta, maxZoomWithData} from "../../gen/props-meta-golarion";
import { timeIndexEnd, timeIndexStart } from "../utils/BasicStyleFilters";

export default function(HOST:string, BUILD_DATA_HASH: number) {

  console.log('HOST', HOST);
  console.log('BUILD_DATA_HASH', BUILD_DATA_HASH);

  type CreatableLayerSpec = (FillLayerSpecification | LineLayerSpecification | SymbolLayerSpecification)&{'source-layer': ExistingLayer};
  type LayerSpec = LayerSpecification&{'source-layer'?: ExistingLayer};

  let colors = {
    water:           'rgb(138, 180, 248)',
    waterDeep:       'rgb(110, 160, 245)',
    waterDarker:     'rgb(  9,  64, 153)',
    road:            'rgb(185, 157,  92)',
    roadDarker:      'rgb( 90,  76,  44)',
    regionBorders:   'rgb(107,  42,  33)',
    regionLabels:    'rgb( 17,  42,  97)',
    regionLabelsOut: 'rgb(213, 195, 138)',
    nationBorders:   'rgb(170, 170, 170)',
    borderDarker:    'rgb( 74,  74,  74)',
    white:           'rgb(255, 255, 255)',
    black:           'rgb( 10,  10,  10)'
  };

  function createLayer(layerId:ExistingLayer, base:Partial<CreatableLayerSpec>):CreatableLayerSpec {
    let layer = propsMeta[layerId];

    let merged = Object.assign({
      id: base.type+'_'+layerId,
      source: 'golarion',
      'source-layer': layerId,
      minzoom: (
          'export_tileMinzoom' in layer.props
          && layer.props.export_tileMinzoom.nullEntries==0
          && layer.props.export_tileMinzoom.minNumber !== 0
        )? layer.props.export_tileMinzoom.minNumber : undefined,
      maxzoom: (
          'export_tileMaxzoom' in layer.props
          && layer.props.export_tileMaxzoom.nullEntries==0
          && layer.props.export_tileMaxzoom.maxNumber !== maxZoomWithData
        )? layer.props.export_tileMaxzoom.maxNumber : undefined,
    }, base) as CreatableLayerSpec;
    

    const baseFilters:ExpressionSpecification[] = [];
    

    //filter for min/max zoom
    if(Prop.minzoom in layer.props && layer.props.minzoom.nonNullEntries > 0)
      baseFilters.push(['any', ['!', ['has', Prop.minzoom]], ['>=', ["zoom"], ['get', Prop.minzoom]]]);
    if(Prop.maxzoom in layer.props && layer.props.maxzoom.nonNullEntries > 0)
      baseFilters.push(['any', ['!', ['has', Prop.maxzoom]], ['<=', ["zoom"], ['get', Prop.maxzoom]]]);

    //filter for time index
    if(layer.hasTime) {
      if('timeIndexStart' in layer.props && layer.props.timeIndexStart.nonNullEntries > 0)
        baseFilters.push(timeIndexStart);
      if('timeIndexEnd' in layer.props && layer.props.timeIndexEnd.nonNullEntries > 0)
        baseFilters.push(timeIndexEnd);
    }

    if(baseFilters.length > 0) {
      if(merged.filter && merged.filter instanceof Array) {
        if(merged.filter[0] === 'all') {
          merged.filter = [...merged.filter, ...baseFilters] as FilterSpecification;
        }
        else {
          merged.filter = ['all', merged.filter, ...baseFilters] as FilterSpecification;
        }
      }
      else {
        merged.filter = ['all', ...baseFilters];
      }
    }

    return merged;
  }

  let layers:LayerSpec[] = [
    {
      id: 'background',
      type: 'background',
      paint: {
        'background-color': colors.waterDeep,
      }
    },
    createLayer('geometry', {
      type: 'fill',
      paint: {
        'fill-color': ['get', Prop.color],
        'fill-antialias': false
      }
    }),
    createLayer('borders', {
      id: 'borders-nations',
      type: 'line',
      filter: ['==', ['get', Prop.borderType], 3],
      paint: {
        'line-color': colors.nationBorders,
        'line-width': ["interpolate", ["exponential", 2], ["zoom"],
          3, .375,
          5, 2,
        ],
      },
      layout: {
        'line-cap': 'round'
      }
    }),
    createLayer('borders', {
      id: 'borders-subregions',
      type: 'line',
      filter: ['==', ['get', Prop.borderType], 2],
      paint: {
        'line-color': colors.nationBorders,
        'line-width': ["interpolate", ["exponential", 2], ["zoom"],
          0, .375,
          3, 2,
        ],
      },
      layout: {
        'line-cap': 'round'
      }
    }),
    createLayer('borders', {
      id: 'borders-regions',
      type: 'line',
      maxzoom: 5,
      filter: ['==', ['get', Prop.borderType], 1],
      paint: {
        'line-color': colors.regionBorders,
        'line-width': 2,
        "line-opacity": ["interpolate", ["exponential", 2], ["zoom"],
          3.5, 1,
          4, 0,
        ],
      },
      layout: {
        'line-cap': 'round'
      }
    }),
    createLayer('borders', {
      id: 'borders-provinces',
      type: 'line',
      filter: ['==', ['get', Prop.borderType], 4],
      minzoom: 4,
      paint: {
        'line-color': colors.nationBorders,
        'line-opacity': ["interpolate", ["exponential", 2], ["zoom"],
          4, 0,
          6, 1,
        ],
        'line-dasharray': [5, 10]
      },
      layout: {
        'line-cap': 'round'
      }
    }),
    createLayer('borders', {
      id: 'borders-districts',
      type: 'line',
      filter: ['==', ['get', Prop.borderType], 5],
      minzoom: 8,
      paint: {
        'line-color': colors.nationBorders,
        'line-opacity': ["interpolate", ["exponential", 2], ["zoom"],
          8, 0,
          10, 1,
        ],
        'line-dasharray': [2, 4]
      },
      layout: {
        'line-cap': 'round'
      }
    }),
    createLayer('line-labels', {
      type: 'symbol',
      layout: {
        'symbol-placement': 'line',
        'text-max-angle': 20,
        'text-field': ['get', Prop.label],
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
        'text-color': ['get', Prop.color],
        'text-halo-color': ['get', Prop.halo],
        'text-halo-width': [
          'interpolate',
          ['linear'],
          ['zoom'],
          5, .125,
          10, 1,
        ],
      }
    }),
    createLayer('locations', {
      id: 'location-icons',
      type: 'symbol',
      layout: {
        'icon-image': ['get', Prop.icon],
        'icon-pitch-alignment': 'map',
        'icon-overlap': 'always',
        'icon-ignore-placement': true,
        'icon-size': ["interpolate", ["exponential", 2], ["zoom"],
          0,            ["^", 2, ["-", -2, ['get', Prop.pregroupMinzoom]]],
          1,            ["^", 2, ["-", -1, ['get', Prop.pregroupMinzoom]]],
          2, ["min", 1, ["^", 2, ["-",  0, ['get', Prop.pregroupMinzoom]]]],
          3, ["min", 1, ["^", 2, ["-",  1, ['get', Prop.pregroupMinzoom]]]],
          4, ["min", 1, ["^", 2, ["-",  2, ['get', Prop.pregroupMinzoom]]]],
          5, ["min", 1, ["^", 2, ["-",  3, ['get', Prop.pregroupMinzoom]]]],
          6, ["min", 1, ["^", 2, ["-",  4, ['get', Prop.pregroupMinzoom]]]],
          7, ["min", 1, ["^", 2, ["-",  5, ['get', Prop.pregroupMinzoom]]]],
          8, ["min", 1, ["^", 2, ["-",  6, ['get', Prop.pregroupMinzoom]]]],
          9, ["min", 1, ["^", 2, ["-",  7, ['get', Prop.pregroupMinzoom]]]],
          10, ["min", 1, ["^", 2, ["-",  8, ['get', Prop.pregroupMinzoom]]]],
        ]
      },
      paint: {
      }
    }),
    createLayer('labels', {
      type: 'symbol',
      layout: {
        'text-field': ['get', Prop.label],
        'text-rotate': ['case', ['global-state', 'rotated'], 0, ['get', Prop.angle]],
        'text-rotation-alignment': ['case', ['global-state', 'rotated'], 'viewport', 'map'],
        'text-font': ['NotoSans-Medium'],
        'text-size': 16,
        "text-overlap": 'always',
      },
      paint: {
        'text-color': ['get', Prop.color],
        'text-halo-color': ['get', Prop.halo],
        'text-halo-width': 1.5
      }
    }),
    createLayer('locations', {
      id: 'location-labels',
      type: 'symbol',
      filter: ['has', 'label'],
      layout: {
        'text-field': ['get', Prop.label],
        'text-font': ['NotoSans-Medium'],
        'text-size': 14,
        'text-variable-anchor': ["left", "right"],
        'text-radial-offset': .5,
        'text-rotation-alignment': ['case', ['global-state', 'rotated'], 'viewport', 'map'],
      },
      paint: {
        'text-color': colors.white,
        'text-halo-color': colors.black,
        'text-halo-width': .8
      }
    }),
    createLayer('province-labels', {
      type: 'symbol',
      layout: {
        'text-field': ['get', Prop.label],
        'text-font': ['NotoSans-Medium'],
        'text-size': ['interpolate', ['linear'], ['zoom'],
          5, 5,
          7, 20,
        ],
        'text-rotation-alignment': ['case', ['global-state', 'rotated'], 'viewport', 'map'],
        'text-variable-anchor': ['center','top','bottom'],
        'symbol-z-order': 'source',
      },
      paint: {
        'text-color': colors.white,
        'text-halo-color': colors.regionLabels,
        'text-halo-width': ['interpolate', ['linear'], ['zoom'],
          5, .375,
          7, 1.5,
        ],
      }
    }),
    createLayer('nation-labels', {
      type: 'symbol',
      filter: ['any',
        ['!', ['get', Prop.inSubregion]],
        ['>', ['zoom'], 4]
      ],
      layout: {
        'text-field': ['get', Prop.label],
        'text-font': ['NotoSans-Medium'],
        'text-size': ['interpolate', ['linear'], ['zoom'],
          4, 10,
          5, 25,
        ],
        'text-rotation-alignment': ['case', ['global-state', 'rotated'], 'viewport', 'map'],
        'text-variable-anchor': ['center','top','bottom'],
        'symbol-z-order': 'source',
      },
      paint: {
        'text-color': colors.white,
        'text-halo-color': colors.regionLabels,
        'text-halo-width': ['interpolate', ['linear'], ['zoom'],
          4, .75,
          5, 1.875,
        ],
      }
    }),
    createLayer('subregion-labels', {
      type: 'symbol',
      layout: {
        'text-field': ['get', Prop.label],
        'text-font': ['NotoSans-Medium'],
        'text-size': ['interpolate', ['linear'], ['zoom'],
          4, 10,
          5, 25,
        ],
        'text-rotation-alignment': ['case', ['global-state', 'rotated'], 'viewport', 'map'],
        'text-variable-anchor': ['center','top','bottom'],
        'symbol-z-order': 'source',
      },
      paint: {
        'text-color': colors.white,
        'text-halo-color': colors.regionLabels,
        'text-halo-width': ['interpolate', ['linear'], ['zoom'],
          4, .75,
          5, 1.875,
        ],
      }
    }),
    createLayer('region-labels', {
      type: 'symbol',
      layout: {
        'text-field': ['get', Prop.label],
        'text-font': ['NotoSans-Medium'],
        'text-size': 20,
        'text-rotation-alignment': ['case', ['global-state', 'rotated'], 'viewport', 'map'],
        'text-variable-anchor': ['center','top','bottom'],
        'symbol-z-order': 'source',
      },
      paint: {
        'text-color': colors.regionLabels,
        'text-halo-color': colors.regionLabelsOut,
        'text-halo-width': 1.5,
      }
    }),
  ];

  return {
    version: 8,
    sources: {
      golarion: {
        type: 'vector',
        attribution: '<a href="https://paizo.com/licenses/communityuse">Paizo CUP</a>, <a href="https://github.com/pf-wikis/mapping#acknowledgments">Acknowledgments</a>',
        url: `pmtiles://${HOST}/golarion.pmtiles?v=${BUILD_DATA_HASH}`,
        encoding: 'mlt'
      },
    },
    state: {
      timeIndex: {
        default: timeMeta.max,
      },
      rotated: {
        default: false
      }
    },
    sprite: `${HOST}/sprites/sprites`,
    layers: layers,
    glyphs: `${HOST}/fonts/{fontstack}/{range}.pbf`,
    transition: {
      duration: 300,
      delay: 0
    },
    sky: {
      'atmosphere-blend': 0.5
    }
  } as StyleSpecification;
}