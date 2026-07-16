import { FillLayerSpecification, LayerSpecification, LineLayerSpecification, SymbolLayerSpecification, RasterLayerSpecification, StyleSpecification, ExpressionFilterSpecification } from "maplibre-gl";
import { ExistingLayer, Prop, propsMeta, maxZoomWithData} from "../../gen/props-meta-golarion";
import { timeIndexEnd, timeIndexStart } from "../utils/BasicStyleFilters";
import { OptionalFields } from "../utils/type-utils";
import { state, StateProp } from "./state";
import { Expression } from "./expression";
import { projection } from "./projection";

export default function(HOST:string, BUILD_DATA_HASH: number) {

  for(let p in state) {
    state[p as StateProp].enableDebug = true;
  }

  console.log('HOST', HOST);
  console.log('BUILD_DATA_HASH', BUILD_DATA_HASH);

  type CreatableLayerSpec = Omit<
    FillLayerSpecification|LineLayerSpecification|SymbolLayerSpecification,
    'filter'|'source-layer'>&{
    'source-layer': ExistingLayer,
    filter?: Expression<boolean>
  };
  type PartialCreateableLayerSpec = OptionalFields<
    CreatableLayerSpec,
    "id"|"source"|"source-layer"
  >;
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

  function createLayer(layerId:ExistingLayer, base:PartialCreateableLayerSpec):LayerSpec {
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
    

    const baseFilters:Expression<boolean>[] = [];
    

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
          merged.filter = [...merged.filter, ...baseFilters];
        }
        else {
          merged.filter = ['all', merged.filter, ...baseFilters];
        }
      }
      else {
        merged.filter = ['all', ...baseFilters];
      }
    }

    return merged as LayerSpec;
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
    {
      id: 'hillshade-mountains',
      type: 'raster',
      source: 'hillshadeMountains',
      paint: {
        'raster-opacity': 0.35,
        'raster-fade-duration': 0,
        'raster-resampling': 'linear'
      }
    } as RasterLayerSpecification,
    {
      id: 'hillshade-hills',
      type: 'raster',
      source: 'hillshadeHills',
      paint: {
        'raster-opacity': 0.25,
        'raster-fade-duration': 0,
        'raster-resampling': 'linear'
      }
    } as RasterLayerSpecification,
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
        visibility: state.showBorders.get(),
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
        visibility: state.showBorders.get(),
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
        visibility: state.showBorders.get(),
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
        visibility: state.showBorders.get(),
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
        visibility: state.showBorders.get(),
        'line-cap': 'round'
      }
    }),
    createLayer('line-labels', {
      type: 'symbol',
      layout: {
        visibility: state.showLabels.get(),
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
        visibility: state.showLocations.get(),
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
        visibility: state.showLabels.get(),
        'text-field': ['get', Prop.label],
        'text-rotate': ['case', state.rotated.get(), 0, ['get', Prop.angle]],
        'text-rotation-alignment': ['case', state.rotated.get(), 'viewport', 'map'],
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
      filter: ['all', ['has', Prop.label], ['>=', ['zoom'], ['+', ['get', Prop.pregroupMinzoom], 5]]],
      layout: {
        visibility: state.showLocations.get(),
        'text-field': ['get', Prop.label],
        'text-font': ['NotoSans-Medium'],
        'text-size': 14,
        'text-variable-anchor': ["left", "right"],
        'text-radial-offset': .5,
        'text-rotation-alignment': ['case', state.rotated.get(), 'viewport', 'map'],
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
        visibility: state.showLabels.get(),
        'text-field': ['get', Prop.label],
        'text-font': ['NotoSans-Medium'],
        'text-size': ['interpolate', ['linear'], ['zoom'],
          5, 5,
          7, 20,
        ],
        'text-rotation-alignment': ['case', state.rotated.get(), 'viewport', 'map'],
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
        visibility: state.showLabels.get(),
        'text-field': ['get', Prop.label],
        'text-font': ['NotoSans-Medium'],
        'text-size': ['interpolate', ['linear'], ['zoom'],
          4, 10,
          5, 25,
        ],
        'text-rotation-alignment': ['case', state.rotated.get(), 'viewport', 'map'],
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
        visibility: state.showLabels.get(),
        'text-field': ['get', Prop.label],
        'text-font': ['NotoSans-Medium'],
        'text-size': ['interpolate', ['linear'], ['zoom'],
          4, 10,
          5, 25,
        ],
        'text-rotation-alignment': ['case', state.rotated.get(), 'viewport', 'map'],
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
        visibility: state.showLabels.get(),
        'text-field': ['get', Prop.label],
        'text-font': ['NotoSans-Medium'],
        'text-size': 20,
        'text-rotation-alignment': ['case', state.rotated.get(), 'viewport', 'map'],
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
    center: [-7,35],
    zoom: 4.5,
    sources: {
      golarion: {
        type: 'vector',
        attribution: '<a href="https://paizo.com/licenses/communityuse">Paizo CUP</a>, <a href="https://github.com/pf-wikis/mapping#acknowledgments">Acknowledgments</a>',
        url: `pmtiles://${HOST}/golarion.pmtiles?v=${BUILD_DATA_HASH}`,
        encoding: 'mlt'
      },
      hillshadeMountains: {
        type: 'image',
        url: `${HOST}/hillshade-mountains.png`,
        coordinates: [
          [-70, 42],   // tl
          [100, 42],  // tr
          [100, -40], // br
          [-70, -40]  // bl
        ]
      },
      hillshadeHills: {
        type: 'image',
        url: `${HOST}/hillshade-hills.png`,
        coordinates: [
          [-70, 42],   // tl
          [100, 42],  // tr
          [100, -40], // br
          [-70, -40]  // bl
        ]
      },
    },
    state: Object.fromEntries(Object.keys(state).map(k=>[k, {default: (state as any)[k].default}])),
    sprite: `${HOST}/sprites/sprites`,
    layers: layers,
    glyphs: `${HOST}/fonts/{fontstack}/{range}.pbf`,
    transition: {
      duration: 300,
      delay: 0
    },
    sky: {
      'atmosphere-blend': 0.5
    },
    projection: projection
  } satisfies StyleSpecification;
}
