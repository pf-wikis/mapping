export const maxZoomWithData = 8;
export const enum Prop {
  angle='angle',
  borderType='borderType',
  color='color',
  export='export',
  export_tileMaxzoom='export_tileMaxzoom',
  export_tileMinzoom='export_tileMinzoom',
  fid='fid',
  halo='halo',
  icon='icon',
  inSubregion='inSubregion',
  label='label',
  maxzoom='maxzoom',
  minzoom='minzoom',
  pregroupMinzoom='pregroupMinzoom',
  timeIndexEnd='timeIndexEnd',
  timeIndexStart='timeIndexStart',
  type='type',
};
export type ExistingLayer = 'borders'|'geometry'|'highlights'|'labels'|'line-labels'|'locations'|'nation-labels'|'province-labels'|'region-labels'|'subregion-labels'|'time-meta';
export const propsMeta = {
  "borders" : {
    "hasTime" : true,
    "props" : {
      "borderType" : {
        "name" : "borderType",
        "maxNumber" : 5,
        "minNumber" : 1,
        "nonNullEntries" : 1133,
        "nullEntries" : 0
      },
      "export" : {
        "name" : "export",
        "nonNullEntries" : 1133,
        "nullEntries" : 0
      },
      "export_tileMaxzoom" : {
        "name" : "export_tileMaxzoom",
        "maxNumber" : 8,
        "minNumber" : 5,
        "nonNullEntries" : 1133,
        "nullEntries" : 0
      },
      "export_tileMinzoom" : {
        "name" : "export_tileMinzoom",
        "maxNumber" : 0,
        "minNumber" : 0,
        "nonNullEntries" : 1133,
        "nullEntries" : 0
      },
      "timeIndexEnd" : {
        "name" : "timeIndexEnd",
        "maxNumber" : 1,
        "minNumber" : -13,
        "nonNullEntries" : 1133,
        "nullEntries" : 0
      },
      "timeIndexStart" : {
        "name" : "timeIndexStart",
        "maxNumber" : 0,
        "minNumber" : -15,
        "nonNullEntries" : 1133,
        "nullEntries" : 0
      }
    }
  },
  "geometry" : {
    "hasTime" : false,
    "props" : {
      "color" : {
        "name" : "color",
        "nonNullEntries" : 19475,
        "nullEntries" : 0
      },
      "export" : {
        "name" : "export",
        "nonNullEntries" : 19475,
        "nullEntries" : 0
      },
      "export_tileMaxzoom" : {
        "name" : "export_tileMaxzoom",
        "maxNumber" : 8,
        "minNumber" : 8,
        "nonNullEntries" : 19475,
        "nullEntries" : 0
      },
      "export_tileMinzoom" : {
        "name" : "export_tileMinzoom",
        "maxNumber" : 0,
        "minNumber" : 0,
        "nonNullEntries" : 19475,
        "nullEntries" : 0
      }
    }
  },
  "highlights" : {
    "hasTime" : true,
    "props" : {
      "export" : {
        "name" : "export",
        "nonNullEntries" : 1063,
        "nullEntries" : 0
      },
      "export_tileMaxzoom" : {
        "name" : "export_tileMaxzoom",
        "maxNumber" : 8,
        "minNumber" : 8,
        "nonNullEntries" : 1063,
        "nullEntries" : 0
      },
      "export_tileMinzoom" : {
        "name" : "export_tileMinzoom",
        "maxNumber" : 0,
        "minNumber" : 0,
        "nonNullEntries" : 1063,
        "nullEntries" : 0
      },
      "label" : {
        "name" : "label",
        "nonNullEntries" : 1063,
        "nullEntries" : 0
      },
      "timeIndexEnd" : {
        "name" : "timeIndexEnd",
        "maxNumber" : 1,
        "minNumber" : -14,
        "nonNullEntries" : 1063,
        "nullEntries" : 0
      },
      "timeIndexStart" : {
        "name" : "timeIndexStart",
        "maxNumber" : 0,
        "minNumber" : -15,
        "nonNullEntries" : 1063,
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
        "nonNullEntries" : 810,
        "nullEntries" : 273
      },
      "color" : {
        "name" : "color",
        "nonNullEntries" : 1083,
        "nullEntries" : 0
      },
      "export" : {
        "name" : "export",
        "nonNullEntries" : 1083,
        "nullEntries" : 0
      },
      "export_tileMaxzoom" : {
        "name" : "export_tileMaxzoom",
        "maxNumber" : 8,
        "minNumber" : 1,
        "nonNullEntries" : 1083,
        "nullEntries" : 0
      },
      "export_tileMinzoom" : {
        "name" : "export_tileMinzoom",
        "maxNumber" : 8,
        "minNumber" : 0,
        "nonNullEntries" : 1083,
        "nullEntries" : 0
      },
      "halo" : {
        "name" : "halo",
        "nonNullEntries" : 1083,
        "nullEntries" : 0
      },
      "label" : {
        "name" : "label",
        "nonNullEntries" : 1083,
        "nullEntries" : 0
      },
      "maxzoom" : {
        "name" : "maxzoom",
        "maxNumber" : 21,
        "minNumber" : 9,
        "nonNullEntries" : 834,
        "nullEntries" : 249
      },
      "minzoom" : {
        "name" : "minzoom",
        "maxNumber" : 18,
        "minNumber" : -2,
        "nonNullEntries" : 483,
        "nullEntries" : 600
      },
      "type" : {
        "name" : "type",
        "nonNullEntries" : 152,
        "nullEntries" : 931
      }
    }
  },
  "line-labels" : {
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
        "maxNumber" : 8,
        "minNumber" : 8,
        "nonNullEntries" : 288,
        "nullEntries" : 0
      },
      "export_tileMinzoom" : {
        "name" : "export_tileMinzoom",
        "maxNumber" : 5,
        "minNumber" : 5,
        "nonNullEntries" : 288,
        "nullEntries" : 0
      },
      "halo" : {
        "name" : "halo",
        "nonNullEntries" : 288,
        "nullEntries" : 0
      },
      "label" : {
        "name" : "label",
        "nonNullEntries" : 288,
        "nullEntries" : 0
      }
    }
  },
  "locations" : {
    "hasTime" : false,
    "props" : {
      "export" : {
        "name" : "export",
        "nonNullEntries" : 2452,
        "nullEntries" : 0
      },
      "export_tileMaxzoom" : {
        "name" : "export_tileMaxzoom",
        "maxNumber" : 8,
        "minNumber" : 2,
        "nonNullEntries" : 2452,
        "nullEntries" : 0
      },
      "export_tileMinzoom" : {
        "name" : "export_tileMinzoom",
        "maxNumber" : 8,
        "minNumber" : 2,
        "nonNullEntries" : 2452,
        "nullEntries" : 0
      },
      "fid" : {
        "name" : "fid",
        "maxNumber" : 2146251733,
        "minNumber" : 23921,
        "nonNullEntries" : 2452,
        "nullEntries" : 0
      },
      "icon" : {
        "name" : "icon",
        "nonNullEntries" : 2452,
        "nullEntries" : 0
      },
      "label" : {
        "name" : "label",
        "nonNullEntries" : 2452,
        "nullEntries" : 0
      },
      "pregroupMinzoom" : {
        "name" : "pregroupMinzoom",
        "maxNumber" : 4,
        "minNumber" : 2,
        "nonNullEntries" : 2452,
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
        "nonNullEntries" : 150,
        "nullEntries" : 0
      },
      "export" : {
        "name" : "export",
        "nonNullEntries" : 150,
        "nullEntries" : 0
      },
      "export_tileMaxzoom" : {
        "name" : "export_tileMaxzoom",
        "maxNumber" : 6,
        "minNumber" : 6,
        "nonNullEntries" : 150,
        "nullEntries" : 0
      },
      "export_tileMinzoom" : {
        "name" : "export_tileMinzoom",
        "maxNumber" : 3,
        "minNumber" : 3,
        "nonNullEntries" : 150,
        "nullEntries" : 0
      },
      "inSubregion" : {
        "name" : "inSubregion",
        "nonNullEntries" : 150,
        "nullEntries" : 0
      },
      "label" : {
        "name" : "label",
        "nonNullEntries" : 150,
        "nullEntries" : 0
      },
      "timeIndexEnd" : {
        "name" : "timeIndexEnd",
        "maxNumber" : 1,
        "minNumber" : -14,
        "nonNullEntries" : 150,
        "nullEntries" : 0
      },
      "timeIndexStart" : {
        "name" : "timeIndexStart",
        "maxNumber" : 0,
        "minNumber" : -15,
        "nonNullEntries" : 150,
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
        "nonNullEntries" : 126,
        "nullEntries" : 0
      },
      "export" : {
        "name" : "export",
        "nonNullEntries" : 126,
        "nullEntries" : 0
      },
      "export_tileMaxzoom" : {
        "name" : "export_tileMaxzoom",
        "maxNumber" : 7,
        "minNumber" : 7,
        "nonNullEntries" : 126,
        "nullEntries" : 0
      },
      "export_tileMinzoom" : {
        "name" : "export_tileMinzoom",
        "maxNumber" : 4,
        "minNumber" : 4,
        "nonNullEntries" : 126,
        "nullEntries" : 0
      },
      "label" : {
        "name" : "label",
        "nonNullEntries" : 126,
        "nullEntries" : 0
      },
      "timeIndexEnd" : {
        "name" : "timeIndexEnd",
        "maxNumber" : 1,
        "minNumber" : -14,
        "nonNullEntries" : 126,
        "nullEntries" : 0
      },
      "timeIndexStart" : {
        "name" : "timeIndexStart",
        "maxNumber" : 0,
        "minNumber" : -15,
        "nonNullEntries" : 126,
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
        "nonNullEntries" : 9,
        "nullEntries" : 0
      },
      "export" : {
        "name" : "export",
        "nonNullEntries" : 9,
        "nullEntries" : 0
      },
      "export_tileMaxzoom" : {
        "name" : "export_tileMaxzoom",
        "maxNumber" : 5,
        "minNumber" : 5,
        "nonNullEntries" : 9,
        "nullEntries" : 0
      },
      "export_tileMinzoom" : {
        "name" : "export_tileMinzoom",
        "maxNumber" : 3,
        "minNumber" : 3,
        "nonNullEntries" : 9,
        "nullEntries" : 0
      },
      "label" : {
        "name" : "label",
        "nonNullEntries" : 9,
        "nullEntries" : 0
      },
      "timeIndexEnd" : {
        "name" : "timeIndexEnd",
        "maxNumber" : 1,
        "minNumber" : -14,
        "nonNullEntries" : 9,
        "nullEntries" : 0
      },
      "timeIndexStart" : {
        "name" : "timeIndexStart",
        "maxNumber" : -3,
        "minNumber" : -15,
        "nonNullEntries" : 9,
        "nullEntries" : 0
      }
    }
  },
  "time-meta" : {
    "hasTime" : false,
    "props" : { }
  }
} as const;
