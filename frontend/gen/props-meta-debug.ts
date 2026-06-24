export const maxZoomWithData = 8;
export const enum Prop {
  city='city',
  export='export',
  export_tileMaxzoom='export_tileMaxzoom',
  export_tileMinzoom='export_tileMinzoom',
  label='label',
  timeIndexEnd='timeIndexEnd',
  timeIndexStart='timeIndexStart',
};
export type ExistingLayer = 'city-polygons'|'highlights'|'time-meta';
export const propsMeta = {
  "time-meta" : {
    "hasTime" : false,
    "props" : { }
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
  "city-polygons" : {
    "hasTime" : false,
    "props" : {
      "city" : {
        "name" : "city",
        "nonNullEntries" : 20,
        "nullEntries" : 0
      }
    }
  }
};
