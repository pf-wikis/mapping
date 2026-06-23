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
export type ExistingLayer = 'time-meta'|'city-polygons'|'highlights';
export const propsMeta = {
  "time-meta" : {
    "hasTime" : false,
    "props" : { }
  },
  "city-polygons" : {
    "hasTime" : false,
    "props" : {
      "city" : {
        "name" : "city",
        "distinctValues" : [ "https://pathfinderwiki.com/wiki/Shoreline", "https://pathfinderwiki.com/wiki/Port_Peril", "https://pathfinderwiki.com/wiki/Korvosa", "https://pathfinderwiki.com/wiki/Ilizmagorti", "https://pathfinderwiki.com/wiki/Copperwood", "https://pathfinderwiki.com/wiki/Vyre", "https://pathfinderwiki.com/wiki/Westerhold", "https://pathfinderwiki.com/wiki/Dawnfoot", "https://pathfinderwiki.com/wiki/Absalom", "https://pathfinderwiki.com/wiki/Magnimar", "https://pathfinderwiki.com/wiki/Kintargo" ],
        "nonNullEntries" : 20,
        "nullEntries" : 0
      }
    }
  },
  "highlights" : {
    "hasTime" : true,
    "props" : {
      "export" : {
        "name" : "export",
        "distinctValues" : [ ],
        "nonNullEntries" : 1063,
        "nullEntries" : 0
      },
      "export_tileMaxzoom" : {
        "name" : "export_tileMaxzoom",
        "distinctValues" : [ 8 ],
        "maxNumber" : 8,
        "minNumber" : 8,
        "nonNullEntries" : 1063,
        "nullEntries" : 0
      },
      "export_tileMinzoom" : {
        "name" : "export_tileMinzoom",
        "distinctValues" : [ 0 ],
        "maxNumber" : 0,
        "minNumber" : 0,
        "nonNullEntries" : 1063,
        "nullEntries" : 0
      },
      "label" : {
        "name" : "label",
        "distinctValues" : [ "Nex", "Jinin", "Gravelands", "Itia", "Lambreth", "New Thassilon", "Ravounel", "Shanguang", "Forest of Spirits", "Valashmai Jungle", "Cheliax" ],
        "nonNullEntries" : 1063,
        "nullEntries" : 0
      },
      "timeIndexEnd" : {
        "name" : "timeIndexEnd",
        "distinctValues" : [ 0, 1, -3, -4, -5, -6, -7, -9, -12, -13, -14 ],
        "maxNumber" : 1,
        "minNumber" : -14,
        "nonNullEntries" : 1063,
        "nullEntries" : 0
      },
      "timeIndexStart" : {
        "name" : "timeIndexStart",
        "distinctValues" : [ 0, -2, -3, -4, -6, -7, -8, -9, -10, -13, -15 ],
        "maxNumber" : 0,
        "minNumber" : -15,
        "nonNullEntries" : 1063,
        "nullEntries" : 0
      }
    }
  }
};
