export const maxZoomWithData = 12;
export const Prop = {
  angle: 'angle',
  borderType: 'borderType',
  color: 'color',
  export: 'export',
  export_tileMaxzoom: 'export_tileMaxzoom',
  export_tileMinzoom: 'export_tileMinzoom',
  fid: 'fid',
  halo: 'halo',
  icon: 'icon',
  inSubregion: 'inSubregion',
  label: 'label',
  maxzoom: 'maxzoom',
  minzoom: 'minzoom',
  pregroupMinzoom: 'pregroupMinzoom',
  timeIndexEnd: 'timeIndexEnd',
  timeIndexStart: 'timeIndexStart',
  type: 'type',
} as const;
export type Prop = typeof Prop[keyof typeof Prop];
export type ExistingLayer = 'borders'|'geometry'|'highlights'|'labels'|'line-labels'|'locations'|'nation-labels'|'province-labels'|'region-labels'|'subregion-labels'|'time-meta';
export const propsMeta = {
  "borders" : {
    "hasTime" : true,
    "props" : {
      "borderType" : {
        "name" : "borderType",
        "maxNumber" : 5,
        "minNumber" : 1,
        "nonNullEntries" : 890,
        "nullEntries" : 0
      },
      "export" : {
        "name" : "export",
        "nonNullEntries" : 890,
        "nullEntries" : 0
      },
      "export_tileMaxzoom" : {
        "name" : "export_tileMaxzoom",
        "maxNumber" : 12,
        "minNumber" : 5,
        "nonNullEntries" : 890,
        "nullEntries" : 0
      },
      "export_tileMinzoom" : {
        "name" : "export_tileMinzoom",
        "maxNumber" : 0,
        "minNumber" : 0,
        "nonNullEntries" : 890,
        "nullEntries" : 0
      },
      "timeIndexEnd" : {
        "name" : "timeIndexEnd",
        "maxNumber" : 1,
        "minNumber" : -14,
        "nonNullEntries" : 890,
        "nullEntries" : 0
      },
      "timeIndexStart" : {
        "name" : "timeIndexStart",
        "maxNumber" : 0,
        "minNumber" : -16,
        "nonNullEntries" : 890,
        "nullEntries" : 0
      }
    }
  },
  "geometry" : {
    "hasTime" : false,
    "props" : {
      "color" : {
        "name" : "color",
        "nonNullEntries" : 288,
        "nullEntries" : 0
      },
      "export" : {
        "name" : "export",
        "nonNullEntries" : 288,
        "nullEntries" : 0
      },
      "export_tileMaxzoom" : {
        "name" : "export_tileMaxzoom",
        "maxNumber" : 12,
        "minNumber" : 12,
        "nonNullEntries" : 288,
        "nullEntries" : 0
      },
      "export_tileMinzoom" : {
        "name" : "export_tileMinzoom",
        "maxNumber" : 0,
        "minNumber" : 0,
        "nonNullEntries" : 288,
        "nullEntries" : 0
      }
    }
  },
  "highlights" : {
    "hasTime" : true,
    "props" : {
      "export" : {
        "name" : "export",
        "nonNullEntries" : 1099,
        "nullEntries" : 0
      },
      "export_tileMaxzoom" : {
        "name" : "export_tileMaxzoom",
        "maxNumber" : 12,
        "minNumber" : 12,
        "nonNullEntries" : 1099,
        "nullEntries" : 0
      },
      "export_tileMinzoom" : {
        "name" : "export_tileMinzoom",
        "maxNumber" : 0,
        "minNumber" : 0,
        "nonNullEntries" : 1099,
        "nullEntries" : 0
      },
      "label" : {
        "name" : "label",
        "nonNullEntries" : 1099,
        "nullEntries" : 0
      },
      "timeIndexEnd" : {
        "name" : "timeIndexEnd",
        "maxNumber" : 1,
        "minNumber" : -15,
        "nonNullEntries" : 1099,
        "nullEntries" : 0
      },
      "timeIndexStart" : {
        "name" : "timeIndexStart",
        "maxNumber" : 0,
        "minNumber" : -16,
        "nonNullEntries" : 1099,
        "nullEntries" : 0
      }
    }
  },
  "labels" : {
    "hasTime" : false,
    "props" : {
      "angle" : {
        "name" : "angle",
        "maxNumber" : 90,
        "minNumber" : -90,
        "nonNullEntries" : 824,
        "nullEntries" : 578
      },
      "color" : {
        "name" : "color",
        "nonNullEntries" : 1402,
        "nullEntries" : 0
      },
      "export" : {
        "name" : "export",
        "nonNullEntries" : 1402,
        "nullEntries" : 0
      },
      "export_tileMaxzoom" : {
        "name" : "export_tileMaxzoom",
        "maxNumber" : 12,
        "minNumber" : 1,
        "nonNullEntries" : 1402,
        "nullEntries" : 0
      },
      "export_tileMinzoom" : {
        "name" : "export_tileMinzoom",
        "maxNumber" : 12,
        "minNumber" : 0,
        "nonNullEntries" : 1402,
        "nullEntries" : 0
      },
      "halo" : {
        "name" : "halo",
        "nonNullEntries" : 1402,
        "nullEntries" : 0
      },
      "label" : {
        "name" : "label",
        "nonNullEntries" : 1402,
        "nullEntries" : 0
      },
      "maxzoom" : {
        "name" : "maxzoom",
        "maxNumber" : 21,
        "minNumber" : 13,
        "nonNullEntries" : 724,
        "nullEntries" : 678
      },
      "minzoom" : {
        "name" : "minzoom",
        "maxNumber" : 18,
        "minNumber" : 13,
        "nonNullEntries" : 396,
        "nullEntries" : 1006
      },
      "type" : {
        "name" : "type",
        "nonNullEntries" : 149,
        "nullEntries" : 1253
      }
    }
  },
  "line-labels" : {
    "hasTime" : false,
    "props" : {
      "color" : {
        "name" : "color",
        "nonNullEntries" : 292,
        "nullEntries" : 0
      },
      "export" : {
        "name" : "export",
        "nonNullEntries" : 292,
        "nullEntries" : 0
      },
      "export_tileMaxzoom" : {
        "name" : "export_tileMaxzoom",
        "maxNumber" : 12,
        "minNumber" : 12,
        "nonNullEntries" : 292,
        "nullEntries" : 0
      },
      "export_tileMinzoom" : {
        "name" : "export_tileMinzoom",
        "maxNumber" : 5,
        "minNumber" : 5,
        "nonNullEntries" : 292,
        "nullEntries" : 0
      },
      "halo" : {
        "name" : "halo",
        "nonNullEntries" : 292,
        "nullEntries" : 0
      },
      "label" : {
        "name" : "label",
        "nonNullEntries" : 292,
        "nullEntries" : 0
      }
    }
  },
  "locations" : {
    "hasTime" : false,
    "props" : {
      "export" : {
        "name" : "export",
        "nonNullEntries" : 3460,
        "nullEntries" : 0
      },
      "export_tileMaxzoom" : {
        "name" : "export_tileMaxzoom",
        "maxNumber" : 12,
        "minNumber" : 2,
        "nonNullEntries" : 3460,
        "nullEntries" : 0
      },
      "export_tileMinzoom" : {
        "name" : "export_tileMinzoom",
        "maxNumber" : 12,
        "minNumber" : 2,
        "nonNullEntries" : 3460,
        "nullEntries" : 0
      },
      "fid" : {
        "name" : "fid",
        "maxNumber" : 2146737791,
        "minNumber" : 34837,
        "nonNullEntries" : 3460,
        "nullEntries" : 0
      },
      "icon" : {
        "name" : "icon",
        "nonNullEntries" : 3460,
        "nullEntries" : 0
      },
      "label" : {
        "name" : "label",
        "nonNullEntries" : 3460,
        "nullEntries" : 0
      },
      "pregroupMinzoom" : {
        "name" : "pregroupMinzoom",
        "maxNumber" : 4,
        "minNumber" : 2,
        "nonNullEntries" : 3460,
        "nullEntries" : 0
      }
    }
  },
  "nation-labels" : {
    "hasTime" : true,
    "props" : {
      "angle" : {
        "name" : "angle",
        "maxNumber" : 87,
        "minNumber" : -89,
        "nonNullEntries" : 152,
        "nullEntries" : 0
      },
      "export" : {
        "name" : "export",
        "nonNullEntries" : 152,
        "nullEntries" : 0
      },
      "export_tileMaxzoom" : {
        "name" : "export_tileMaxzoom",
        "maxNumber" : 6,
        "minNumber" : 6,
        "nonNullEntries" : 152,
        "nullEntries" : 0
      },
      "export_tileMinzoom" : {
        "name" : "export_tileMinzoom",
        "maxNumber" : 3,
        "minNumber" : 3,
        "nonNullEntries" : 152,
        "nullEntries" : 0
      },
      "inSubregion" : {
        "name" : "inSubregion",
        "nonNullEntries" : 152,
        "nullEntries" : 0
      },
      "label" : {
        "name" : "label",
        "nonNullEntries" : 152,
        "nullEntries" : 0
      },
      "timeIndexEnd" : {
        "name" : "timeIndexEnd",
        "maxNumber" : 1,
        "minNumber" : -15,
        "nonNullEntries" : 152,
        "nullEntries" : 0
      },
      "timeIndexStart" : {
        "name" : "timeIndexStart",
        "maxNumber" : 0,
        "minNumber" : -16,
        "nonNullEntries" : 152,
        "nullEntries" : 0
      }
    }
  },
  "province-labels" : {
    "hasTime" : true,
    "props" : {
      "angle" : {
        "name" : "angle",
        "maxNumber" : 82,
        "minNumber" : -88,
        "nonNullEntries" : 137,
        "nullEntries" : 0
      },
      "export" : {
        "name" : "export",
        "nonNullEntries" : 137,
        "nullEntries" : 0
      },
      "export_tileMaxzoom" : {
        "name" : "export_tileMaxzoom",
        "maxNumber" : 7,
        "minNumber" : 7,
        "nonNullEntries" : 137,
        "nullEntries" : 0
      },
      "export_tileMinzoom" : {
        "name" : "export_tileMinzoom",
        "maxNumber" : 4,
        "minNumber" : 4,
        "nonNullEntries" : 137,
        "nullEntries" : 0
      },
      "label" : {
        "name" : "label",
        "nonNullEntries" : 137,
        "nullEntries" : 0
      },
      "timeIndexEnd" : {
        "name" : "timeIndexEnd",
        "maxNumber" : 1,
        "minNumber" : -15,
        "nonNullEntries" : 137,
        "nullEntries" : 0
      },
      "timeIndexStart" : {
        "name" : "timeIndexStart",
        "maxNumber" : 0,
        "minNumber" : -16,
        "nonNullEntries" : 137,
        "nullEntries" : 0
      }
    }
  },
  "region-labels" : {
    "hasTime" : true,
    "props" : {
      "angle" : {
        "name" : "angle",
        "maxNumber" : 71,
        "minNumber" : 0,
        "nonNullEntries" : 13,
        "nullEntries" : 0
      },
      "export" : {
        "name" : "export",
        "nonNullEntries" : 13,
        "nullEntries" : 0
      },
      "export_tileMaxzoom" : {
        "name" : "export_tileMaxzoom",
        "maxNumber" : 3,
        "minNumber" : 3,
        "nonNullEntries" : 13,
        "nullEntries" : 0
      },
      "export_tileMinzoom" : {
        "name" : "export_tileMinzoom",
        "maxNumber" : 1,
        "minNumber" : 1,
        "nonNullEntries" : 13,
        "nullEntries" : 0
      },
      "label" : {
        "name" : "label",
        "nonNullEntries" : 13,
        "nullEntries" : 0
      },
      "timeIndexEnd" : {
        "name" : "timeIndexEnd",
        "maxNumber" : 1,
        "minNumber" : -3,
        "nonNullEntries" : 13,
        "nullEntries" : 0
      },
      "timeIndexStart" : {
        "name" : "timeIndexStart",
        "maxNumber" : 0,
        "minNumber" : -6,
        "nonNullEntries" : 13,
        "nullEntries" : 0
      }
    }
  },
  "subregion-labels" : {
    "hasTime" : true,
    "props" : {
      "angle" : {
        "name" : "angle",
        "maxNumber" : 60,
        "minNumber" : 0,
        "nonNullEntries" : 10,
        "nullEntries" : 0
      },
      "export" : {
        "name" : "export",
        "nonNullEntries" : 10,
        "nullEntries" : 0
      },
      "export_tileMaxzoom" : {
        "name" : "export_tileMaxzoom",
        "maxNumber" : 5,
        "minNumber" : 5,
        "nonNullEntries" : 10,
        "nullEntries" : 0
      },
      "export_tileMinzoom" : {
        "name" : "export_tileMinzoom",
        "maxNumber" : 3,
        "minNumber" : 3,
        "nonNullEntries" : 10,
        "nullEntries" : 0
      },
      "label" : {
        "name" : "label",
        "nonNullEntries" : 10,
        "nullEntries" : 0
      },
      "timeIndexEnd" : {
        "name" : "timeIndexEnd",
        "maxNumber" : 1,
        "minNumber" : -15,
        "nonNullEntries" : 10,
        "nullEntries" : 0
      },
      "timeIndexStart" : {
        "name" : "timeIndexStart",
        "maxNumber" : -3,
        "minNumber" : -16,
        "nonNullEntries" : 10,
        "nullEntries" : 0
      }
    }
  },
  "time-meta" : {
    "hasTime" : false,
    "props" : { }
  }
} as const;
export const colors = {
  "rivers" : "#8AB4F8",
  "waters" : "#8AB4F8",
  "mountains" : "#DED4B8",
  "swamps" : "#B7C5BC",
  "shallow-waters" : "#8AB4F8",
  "districts" : "#D4CCB9",
  "black" : "#0A0A0A",
  "roads" : "#B99D5C",
  "ice" : "#FFFFFFBF",
  "swamp" : "#B7C5BC",
  "forests" : "#BBE2C6",
  "bordersRegion" : "#6B2A21",
  "continents" : "#F8F1E1",
  "labelsRegionOuter" : "#D5C38A",
  "deserts" : "#FFF7BE",
  "white" : "#FFF",
  "buildings" : "#789",
  "waterDeep" : "#6EA0F5",
  "hills" : "#EBE3CD",
  "land" : "#F8F1E1",
  "labelsRegion" : "#112A61",
  "bordersNation" : "#AAA"
};
