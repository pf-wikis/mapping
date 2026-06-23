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
  link='link',
  maxzoom='maxzoom',
  minzoom='minzoom',
  pregroupMinzoom='pregroupMinzoom',
  timeIndexEnd='timeIndexEnd',
  timeIndexStart='timeIndexStart',
  type='type',
};
export type ExistingLayer = 'labels'|'line-labels'|'highlights'|'borders'|'geometry'|'subregion-labels'|'nation-labels'|'region-labels'|'time-meta'|'province-labels'|'locations';
export const propsMeta = {
  "labels" : {
    "hasTime" : false,
    "props" : {
      "angle" : {
        "name" : "angle",
        "distinctValues" : [ 0, -79, 15, -29, -76, -59, -74, 73, 87, -22, 18 ],
        "maxNumber" : 90,
        "minNumber" : -90,
        "nonNullEntries" : 809,
        "nullEntries" : 273
      },
      "color" : {
        "name" : "color",
        "distinctValues" : [ "#322218", "#2A2600", "#262626", "#1D253C", "#17263F", "#1F2822", "#272622", "#2A2519", "#361F2B", "#28261F", "#082646" ],
        "nonNullEntries" : 1082,
        "nullEntries" : 0
      },
      "export" : {
        "name" : "export",
        "distinctValues" : [ ],
        "nonNullEntries" : 1082,
        "nullEntries" : 0
      },
      "export_tileMaxzoom" : {
        "name" : "export_tileMaxzoom",
        "distinctValues" : [ 1, 2, 3, 4, 5, 6, 7, 8 ],
        "maxNumber" : 8,
        "minNumber" : 1,
        "nonNullEntries" : 1082,
        "nullEntries" : 0
      },
      "export_tileMinzoom" : {
        "name" : "export_tileMinzoom",
        "distinctValues" : [ 0, 1, 2, 3, 4, 5, 6, 7, 8 ],
        "maxNumber" : 8,
        "minNumber" : 0,
        "nonNullEntries" : 1082,
        "nullEntries" : 0
      },
      "fid" : {
        "name" : "fid",
        "distinctValues" : [ 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 ],
        "maxNumber" : 1081,
        "minNumber" : 0,
        "nonNullEntries" : 1082,
        "nullEntries" : 0
      },
      "halo" : {
        "name" : "halo",
        "distinctValues" : [ "#9BA6D1", "#E0DBCC", "#EDE8DE", "#FFF", "#F4F1E7", "#7698DA", "#B8CFFB", "#694D39", "#EDF8F0", "#D1E1D7", "#87556F" ],
        "nonNullEntries" : 1082,
        "nullEntries" : 0
      },
      "label" : {
        "name" : "label",
        "distinctValues" : [ "Azlant", "Arcadia", "Whistling Plains", "Tashen Yakuta", "Ikkaku Peninsula", "Tian Xia", "Garund", "Sarusan", "Avistan", "Casmaron", "Crown of the World" ],
        "nonNullEntries" : 1082,
        "nullEntries" : 0
      },
      "maxzoom" : {
        "name" : "maxzoom",
        "distinctValues" : [ 16, 17, 18, 19, 9, 10, 11, 12, 13, 14, 15 ],
        "maxNumber" : 21,
        "minNumber" : 9,
        "nonNullEntries" : 833,
        "nullEntries" : 249
      },
      "minzoom" : {
        "name" : "minzoom",
        "distinctValues" : [ 16, -1, -2, 17, 9, 10, 11, 12, 13, 14, 15 ],
        "maxNumber" : 18,
        "minNumber" : -2,
        "nonNullEntries" : 483,
        "nullEntries" : 599
      },
      "type" : {
        "name" : "type",
        "distinctValues" : [ "waters", "land" ],
        "nonNullEntries" : 152,
        "nullEntries" : 930
      }
    }
  },
  "line-labels" : {
    "hasTime" : false,
    "props" : {
      "color" : {
        "name" : "color",
        "distinctValues" : [ "#B8CFFB", "#D8B76C" ],
        "nonNullEntries" : 288,
        "nullEntries" : 0
      },
      "export" : {
        "name" : "export",
        "distinctValues" : [ ],
        "nonNullEntries" : 288,
        "nullEntries" : 0
      },
      "export_tileMaxzoom" : {
        "name" : "export_tileMaxzoom",
        "distinctValues" : [ 8 ],
        "maxNumber" : 8,
        "minNumber" : 8,
        "nonNullEntries" : 288,
        "nullEntries" : 0
      },
      "export_tileMinzoom" : {
        "name" : "export_tileMinzoom",
        "distinctValues" : [ 5 ],
        "maxNumber" : 5,
        "minNumber" : 5,
        "nonNullEntries" : 288,
        "nullEntries" : 0
      },
      "fid" : {
        "name" : "fid",
        "distinctValues" : [ 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 ],
        "maxNumber" : 287,
        "minNumber" : 0,
        "nonNullEntries" : 288,
        "nullEntries" : 0
      },
      "halo" : {
        "name" : "halo",
        "distinctValues" : [ "#2D2511", "#082646" ],
        "nonNullEntries" : 288,
        "nullEntries" : 0
      },
      "label" : {
        "name" : "label",
        "distinctValues" : [ "Yolubilis River", "Shining River", "Southern Usk River", "Adivian River", "Usk River", "Travek River", "Tomarsulk River", "Sedna River", "Sulfur River", "Maiestas River", "Coldrun River" ],
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
        "distinctValues" : [ ],
        "nonNullEntries" : 1044,
        "nullEntries" : 0
      },
      "export_tileMaxzoom" : {
        "name" : "export_tileMaxzoom",
        "distinctValues" : [ 8 ],
        "maxNumber" : 8,
        "minNumber" : 8,
        "nonNullEntries" : 1044,
        "nullEntries" : 0
      },
      "export_tileMinzoom" : {
        "name" : "export_tileMinzoom",
        "distinctValues" : [ 0 ],
        "maxNumber" : 0,
        "minNumber" : 0,
        "nonNullEntries" : 1044,
        "nullEntries" : 0
      },
      "fid" : {
        "name" : "fid",
        "distinctValues" : [ 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 ],
        "maxNumber" : 1043,
        "minNumber" : 0,
        "nonNullEntries" : 1044,
        "nullEntries" : 0
      },
      "label" : {
        "name" : "label",
        "distinctValues" : [ "Nex", "Jinin", "Gravelands", "Itia", "Lambreth", "New Thassilon", "Ravounel", "Shanguang", "Forest of Spirits", "Absalom", "Valashmai Jungle" ],
        "nonNullEntries" : 1044,
        "nullEntries" : 0
      },
      "timeIndexEnd" : {
        "name" : "timeIndexEnd",
        "distinctValues" : [ 0, -1, 1, -2, -3, -4, -5, -6, -7, -8, -9 ],
        "maxNumber" : 1,
        "minNumber" : -9,
        "nonNullEntries" : 1044,
        "nullEntries" : 0
      },
      "timeIndexStart" : {
        "name" : "timeIndexStart",
        "distinctValues" : [ 0, -1, -2, -3, -4, -5, -6, -7, -8, -9, -10 ],
        "maxNumber" : 0,
        "minNumber" : -10,
        "nonNullEntries" : 1044,
        "nullEntries" : 0
      }
    }
  },
  "borders" : {
    "hasTime" : true,
    "props" : {
      "borderType" : {
        "name" : "borderType",
        "distinctValues" : [ 1, 2, 3, 4, 5 ],
        "maxNumber" : 5,
        "minNumber" : 1,
        "nonNullEntries" : 1127,
        "nullEntries" : 0
      },
      "export" : {
        "name" : "export",
        "distinctValues" : [ ],
        "nonNullEntries" : 1127,
        "nullEntries" : 0
      },
      "export_tileMaxzoom" : {
        "name" : "export_tileMaxzoom",
        "distinctValues" : [ 5, 8 ],
        "maxNumber" : 8,
        "minNumber" : 5,
        "nonNullEntries" : 1127,
        "nullEntries" : 0
      },
      "export_tileMinzoom" : {
        "name" : "export_tileMinzoom",
        "distinctValues" : [ 0 ],
        "maxNumber" : 0,
        "minNumber" : 0,
        "nonNullEntries" : 1127,
        "nullEntries" : 0
      },
      "fid" : {
        "name" : "fid",
        "distinctValues" : [ 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 ],
        "maxNumber" : 1126,
        "minNumber" : 0,
        "nonNullEntries" : 1127,
        "nullEntries" : 0
      },
      "timeIndexEnd" : {
        "name" : "timeIndexEnd",
        "distinctValues" : [ 0, -1, 1, -3, -5, -6, -7, -8, -9 ],
        "maxNumber" : 1,
        "minNumber" : -9,
        "nonNullEntries" : 1127,
        "nullEntries" : 0
      },
      "timeIndexStart" : {
        "name" : "timeIndexStart",
        "distinctValues" : [ 0, -1, -3, -4, -5, -6, -7, -8, -9, -10 ],
        "maxNumber" : 0,
        "minNumber" : -10,
        "nonNullEntries" : 1127,
        "nullEntries" : 0
      }
    }
  },
  "geometry" : {
    "hasTime" : false,
    "props" : {
      "color" : {
        "name" : "color",
        "distinctValues" : [ "#E1ECFD", "#FDFBF7", "#F9F7F2", "#EBE3CD", "#DED4B8", "#F6F4ED", "#DAE7FC", "#B7C5BC", "#8AB4F8", "#F8F1E1", "#FFF7BE" ],
        "nonNullEntries" : 19478,
        "nullEntries" : 0
      },
      "export" : {
        "name" : "export",
        "distinctValues" : [ ],
        "nonNullEntries" : 19478,
        "nullEntries" : 0
      },
      "export_tileMaxzoom" : {
        "name" : "export_tileMaxzoom",
        "distinctValues" : [ 8 ],
        "maxNumber" : 8,
        "minNumber" : 8,
        "nonNullEntries" : 19478,
        "nullEntries" : 0
      },
      "export_tileMinzoom" : {
        "name" : "export_tileMinzoom",
        "distinctValues" : [ 0 ],
        "maxNumber" : 0,
        "minNumber" : 0,
        "nonNullEntries" : 19478,
        "nullEntries" : 0
      },
      "fid" : {
        "name" : "fid",
        "distinctValues" : [ 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 ],
        "maxNumber" : 19477,
        "minNumber" : 0,
        "nonNullEntries" : 19478,
        "nullEntries" : 0
      }
    }
  },
  "subregion-labels" : {
    "hasTime" : true,
    "props" : {
      "angle" : {
        "name" : "angle",
        "distinctValues" : [ 0, 60 ],
        "maxNumber" : 60,
        "minNumber" : 0,
        "nonNullEntries" : 7,
        "nullEntries" : 0
      },
      "export" : {
        "name" : "export",
        "distinctValues" : [ ],
        "nonNullEntries" : 7,
        "nullEntries" : 0
      },
      "export_tileMaxzoom" : {
        "name" : "export_tileMaxzoom",
        "distinctValues" : [ 5 ],
        "maxNumber" : 5,
        "minNumber" : 5,
        "nonNullEntries" : 7,
        "nullEntries" : 0
      },
      "export_tileMinzoom" : {
        "name" : "export_tileMinzoom",
        "distinctValues" : [ 3 ],
        "maxNumber" : 3,
        "minNumber" : 3,
        "nonNullEntries" : 7,
        "nullEntries" : 0
      },
      "fid" : {
        "name" : "fid",
        "distinctValues" : [ 0, 1, 2, 3, 4, 5, 6 ],
        "maxNumber" : 6,
        "minNumber" : 0,
        "nonNullEntries" : 7,
        "nullEntries" : 0
      },
      "label" : {
        "name" : "label",
        "distinctValues" : [ "River Kingdoms", "Kelesh", "Iobaria", "Varisia", "Iblydos" ],
        "nonNullEntries" : 7,
        "nullEntries" : 0
      },
      "timeIndexEnd" : {
        "name" : "timeIndexEnd",
        "distinctValues" : [ 1, -3, -9 ],
        "maxNumber" : 1,
        "minNumber" : -9,
        "nonNullEntries" : 7,
        "nullEntries" : 0
      },
      "timeIndexStart" : {
        "name" : "timeIndexStart",
        "distinctValues" : [ -3, -9, -10 ],
        "maxNumber" : -3,
        "minNumber" : -10,
        "nonNullEntries" : 7,
        "nullEntries" : 0
      }
    }
  },
  "nation-labels" : {
    "hasTime" : true,
    "props" : {
      "angle" : {
        "name" : "angle",
        "distinctValues" : [ 0, -78, 30, -60, 13, -89, -56, -71, 38, -52, -17 ],
        "maxNumber" : 87,
        "minNumber" : -89,
        "nonNullEntries" : 142,
        "nullEntries" : 0
      },
      "export" : {
        "name" : "export",
        "distinctValues" : [ ],
        "nonNullEntries" : 142,
        "nullEntries" : 0
      },
      "export_tileMaxzoom" : {
        "name" : "export_tileMaxzoom",
        "distinctValues" : [ 6 ],
        "maxNumber" : 6,
        "minNumber" : 6,
        "nonNullEntries" : 142,
        "nullEntries" : 0
      },
      "export_tileMinzoom" : {
        "name" : "export_tileMinzoom",
        "distinctValues" : [ 3 ],
        "maxNumber" : 3,
        "minNumber" : 3,
        "nonNullEntries" : 142,
        "nullEntries" : 0
      },
      "fid" : {
        "name" : "fid",
        "distinctValues" : [ 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 ],
        "maxNumber" : 141,
        "minNumber" : 0,
        "nonNullEntries" : 142,
        "nullEntries" : 0
      },
      "inSubregion" : {
        "name" : "inSubregion",
        "distinctValues" : [ false, true ],
        "nonNullEntries" : 142,
        "nullEntries" : 0
      },
      "label" : {
        "name" : "label",
        "distinctValues" : [ "Minkai", "Nagajor", "Vudra", "Shanguang", "Forest of Spirits", "Hongal", "Lingshen", "Wall of Heaven", "Holomog", "Valashmai Jungle", "Minata" ],
        "nonNullEntries" : 142,
        "nullEntries" : 0
      },
      "timeIndexEnd" : {
        "name" : "timeIndexEnd",
        "distinctValues" : [ -1, 0, 1, -2, -3, -4, -5, -6, -7, -8, -9 ],
        "maxNumber" : 1,
        "minNumber" : -9,
        "nonNullEntries" : 142,
        "nullEntries" : 0
      },
      "timeIndexStart" : {
        "name" : "timeIndexStart",
        "distinctValues" : [ -1, 0, -2, -3, -4, -5, -6, -7, -8, -9, -10 ],
        "maxNumber" : 0,
        "minNumber" : -10,
        "nonNullEntries" : 142,
        "nullEntries" : 0
      }
    }
  },
  "region-labels" : {
    "hasTime" : true,
    "props" : {
      "angle" : {
        "name" : "angle",
        "distinctValues" : [ 0, 71 ],
        "maxNumber" : 71,
        "minNumber" : 0,
        "nonNullEntries" : 13,
        "nullEntries" : 0
      },
      "export" : {
        "name" : "export",
        "distinctValues" : [ ],
        "nonNullEntries" : 13,
        "nullEntries" : 0
      },
      "export_tileMaxzoom" : {
        "name" : "export_tileMaxzoom",
        "distinctValues" : [ 3 ],
        "maxNumber" : 3,
        "minNumber" : 3,
        "nonNullEntries" : 13,
        "nullEntries" : 0
      },
      "export_tileMinzoom" : {
        "name" : "export_tileMinzoom",
        "distinctValues" : [ 1 ],
        "maxNumber" : 1,
        "minNumber" : 1,
        "nonNullEntries" : 13,
        "nullEntries" : 0
      },
      "fid" : {
        "name" : "fid",
        "distinctValues" : [ 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 ],
        "maxNumber" : 12,
        "minNumber" : 0,
        "nonNullEntries" : 13,
        "nullEntries" : 0
      },
      "label" : {
        "name" : "label",
        "distinctValues" : [ "Old Cheliax", "Mwangi Expanse", "Shining Kingdoms", "Golden Road", "Impossible Lands", "Absalom", "Saga Lands", "High Seas", "Eye of Dread", "Broken Lands" ],
        "nonNullEntries" : 13,
        "nullEntries" : 0
      },
      "timeIndexEnd" : {
        "name" : "timeIndexEnd",
        "distinctValues" : [ 0, 1, -3 ],
        "maxNumber" : 1,
        "minNumber" : -3,
        "nonNullEntries" : 13,
        "nullEntries" : 0
      },
      "timeIndexStart" : {
        "name" : "timeIndexStart",
        "distinctValues" : [ 0, -3, -6 ],
        "maxNumber" : 0,
        "minNumber" : -6,
        "nonNullEntries" : 13,
        "nullEntries" : 0
      }
    }
  },
  "time-meta" : {
    "hasTime" : false,
    "props" : { }
  },
  "province-labels" : {
    "hasTime" : true,
    "props" : {
      "angle" : {
        "name" : "angle",
        "distinctValues" : [ 0, -28, 26, 23, 22, 20, -83, 35, 82, 49, 17 ],
        "maxNumber" : 82,
        "minNumber" : -88,
        "nonNullEntries" : 118,
        "nullEntries" : 0
      },
      "export" : {
        "name" : "export",
        "distinctValues" : [ ],
        "nonNullEntries" : 118,
        "nullEntries" : 0
      },
      "export_tileMaxzoom" : {
        "name" : "export_tileMaxzoom",
        "distinctValues" : [ 7 ],
        "maxNumber" : 7,
        "minNumber" : 7,
        "nonNullEntries" : 118,
        "nullEntries" : 0
      },
      "export_tileMinzoom" : {
        "name" : "export_tileMinzoom",
        "distinctValues" : [ 4 ],
        "maxNumber" : 4,
        "minNumber" : 4,
        "nonNullEntries" : 118,
        "nullEntries" : 0
      },
      "fid" : {
        "name" : "fid",
        "distinctValues" : [ 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 ],
        "maxNumber" : 117,
        "minNumber" : 0,
        "nonNullEntries" : 118,
        "nullEntries" : 0
      },
      "label" : {
        "name" : "label",
        "distinctValues" : [ "Koroli", "Crying Jungle", "Golden Basin", "The Cradle", "Divine Garden", "Andoran", "Falling Mountains", "Western Ghats", "Open Bridge", "Narhari Desert", "Wide Water" ],
        "nonNullEntries" : 118,
        "nullEntries" : 0
      },
      "timeIndexEnd" : {
        "name" : "timeIndexEnd",
        "distinctValues" : [ 0, 1, -3, -5, -7, -8 ],
        "maxNumber" : 1,
        "minNumber" : -8,
        "nonNullEntries" : 118,
        "nullEntries" : 0
      },
      "timeIndexStart" : {
        "name" : "timeIndexStart",
        "distinctValues" : [ 0, -3, -6, -10 ],
        "maxNumber" : 0,
        "minNumber" : -10,
        "nonNullEntries" : 118,
        "nullEntries" : 0
      }
    }
  },
  "locations" : {
    "hasTime" : false,
    "props" : {
      "export" : {
        "name" : "export",
        "distinctValues" : [ ],
        "nonNullEntries" : 2801,
        "nullEntries" : 0
      },
      "export_tileMaxzoom" : {
        "name" : "export_tileMaxzoom",
        "distinctValues" : [ 1, 2, 3, 4, 5, 6, 7, 8 ],
        "maxNumber" : 8,
        "minNumber" : 1,
        "nonNullEntries" : 2801,
        "nullEntries" : 0
      },
      "export_tileMinzoom" : {
        "name" : "export_tileMinzoom",
        "distinctValues" : [ 1, 2, 3, 4, 5, 6, 7, 8 ],
        "maxNumber" : 8,
        "minNumber" : 1,
        "nonNullEntries" : 2801,
        "nullEntries" : 0
      },
      "fid" : {
        "name" : "fid",
        "distinctValues" : [ 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 ],
        "maxNumber" : 2790,
        "minNumber" : 0,
        "nonNullEntries" : 2801,
        "nullEntries" : 0
      },
      "icon" : {
        "name" : "icon",
        "distinctValues" : [ "city-small", "location-mountain", "city-large", "location-other", "location-graveyard", "city-medium-capital", "city-major", "city-large-capital", "city-medium", "location-city-ruins", "city-major-capital" ],
        "nonNullEntries" : 2801,
        "nullEntries" : 0
      },
      "label" : {
        "name" : "label",
        "distinctValues" : [ "Aaminiut", "Akafuto", "Vannarak", "Darinkhuur", "Jayat-Von", "Ordu-Aganhei", "Segada", "Ketskerlet", "Jaagiin", "Nokkaichi", "Ul-Angorn" ],
        "nonNullEntries" : 2801,
        "nullEntries" : 0
      },
      "link" : {
        "name" : "link",
        "distinctValues" : [ "https://pathfinderwiki.com/wiki/PathfinderWiki:Map_Locations_Without_Articles" ],
        "nonNullEntries" : 134,
        "nullEntries" : 2667
      },
      "pregroupMinzoom" : {
        "name" : "pregroupMinzoom",
        "distinctValues" : [ 1, 2, 3 ],
        "maxNumber" : 3,
        "minNumber" : 1,
        "nonNullEntries" : 2801,
        "nullEntries" : 0
      }
    }
  }
};
