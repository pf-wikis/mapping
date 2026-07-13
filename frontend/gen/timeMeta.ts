const oldest = {
  "id" : -16,
  "label" : "before <a href=\"https://pathfinderwiki.com/wiki/4609_AR\">4609 AR</a>",
  "representativeYear" : 4608,
  "start" : null,
  "end" : 4609
} as const;
const latest = {
  "id" : 0,
  "label" : "since <a href=\"https://pathfinderwiki.com/wiki/4726_AR\">4726 AR</a>",
  "representativeYear" : 4726,
  "start" : 4726,
  "end" : null
} as const;
const data = [oldest,...[ {
  "id" : -15,
  "label" : "<a href=\"https://pathfinderwiki.com/wiki/4609_AR\">4609 AR</a> - <a href=\"https://pathfinderwiki.com/wiki/4631_AR\">4631 AR</a>",
  "representativeYear" : 4609,
  "start" : 4609,
  "end" : 4632
}, {
  "id" : -14,
  "label" : "<a href=\"https://pathfinderwiki.com/wiki/4632_AR\">4632 AR</a>",
  "representativeYear" : 4632,
  "start" : 4632,
  "end" : 4633
}, {
  "id" : -13,
  "label" : "<a href=\"https://pathfinderwiki.com/wiki/4633_AR\">4633 AR</a> - <a href=\"https://pathfinderwiki.com/wiki/4639_AR\">4639 AR</a>",
  "representativeYear" : 4633,
  "start" : 4633,
  "end" : 4640
}, {
  "id" : -12,
  "label" : "<a href=\"https://pathfinderwiki.com/wiki/4640_AR\">4640 AR</a> - <a href=\"https://pathfinderwiki.com/wiki/4654_AR\">4654 AR</a>",
  "representativeYear" : 4640,
  "start" : 4640,
  "end" : 4655
}, {
  "id" : -11,
  "label" : "<a href=\"https://pathfinderwiki.com/wiki/4655_AR\">4655 AR</a> - <a href=\"https://pathfinderwiki.com/wiki/4660_AR\">4660 AR</a>",
  "representativeYear" : 4655,
  "start" : 4655,
  "end" : 4661
}, {
  "id" : -10,
  "label" : "<a href=\"https://pathfinderwiki.com/wiki/4661_AR\">4661 AR</a> - <a href=\"https://pathfinderwiki.com/wiki/4666_AR\">4666 AR</a>",
  "representativeYear" : 4661,
  "start" : 4661,
  "end" : 4667
}, {
  "id" : -9,
  "label" : "<a href=\"https://pathfinderwiki.com/wiki/4667_AR\">4667 AR</a> - <a href=\"https://pathfinderwiki.com/wiki/4668_AR\">4668 AR</a>",
  "representativeYear" : 4667,
  "start" : 4667,
  "end" : 4669
}, {
  "id" : -8,
  "label" : "<a href=\"https://pathfinderwiki.com/wiki/4669_AR\">4669 AR</a> - <a href=\"https://pathfinderwiki.com/wiki/4709_AR\">4709 AR</a>",
  "representativeYear" : 4669,
  "start" : 4669,
  "end" : 4710
}, {
  "id" : -7,
  "label" : "<a href=\"https://pathfinderwiki.com/wiki/4710_AR\">4710 AR</a>",
  "representativeYear" : 4710,
  "start" : 4710,
  "end" : 4711
}, {
  "id" : -6,
  "label" : "<a href=\"https://pathfinderwiki.com/wiki/4711_AR\">4711 AR</a> - <a href=\"https://pathfinderwiki.com/wiki/4715_AR\">4715 AR</a>",
  "representativeYear" : 4711,
  "start" : 4711,
  "end" : 4716
}, {
  "id" : -5,
  "label" : "<a href=\"https://pathfinderwiki.com/wiki/4716_AR\">4716 AR</a>",
  "representativeYear" : 4716,
  "start" : 4716,
  "end" : 4717
}, {
  "id" : -4,
  "label" : "<a href=\"https://pathfinderwiki.com/wiki/4717_AR\">4717 AR</a>",
  "representativeYear" : 4717,
  "start" : 4717,
  "end" : 4718
}, {
  "id" : -3,
  "label" : "<a href=\"https://pathfinderwiki.com/wiki/4718_AR\">4718 AR</a>",
  "representativeYear" : 4718,
  "start" : 4718,
  "end" : 4719
}, {
  "id" : -2,
  "label" : "<a href=\"https://pathfinderwiki.com/wiki/4719_AR\">4719 AR</a>",
  "representativeYear" : 4719,
  "start" : 4719,
  "end" : 4720
}, {
  "id" : -1,
  "label" : "<a href=\"https://pathfinderwiki.com/wiki/4720_AR\">4720 AR</a> - <a href=\"https://pathfinderwiki.com/wiki/4725_AR\">4725 AR</a>",
  "representativeYear" : 4720,
  "start" : 4720,
  "end" : 4726
} ],latest] as const;
export type TimeSlice = typeof data[number];
export default {
	byId(id:number):TimeSlice {
		let index = id + 16;
		if(index < 0 || index > data.length)
			throw new Error(`No time entry found for id ${id}`);
		return data[index];
	},
	byYear(year:number):TimeSlice {
		for(let t of data) {
			if((t.start??-Infinity)<=year && (t.end??Infinity)>year) {
				return t;
			}
		}
		throw new Error(`No time entry found for year ${year}`);
	},
	oldest: oldest,
	latest: latest
} as const
