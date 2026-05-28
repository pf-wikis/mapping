import timeMetaRaw from "../../gen/time-meta.json";

const timeMeta = {
    byId: new Map(timeMetaRaw.map(t => [t.id, t])),
    min: Math.min(...timeMetaRaw.map(t => t.id)),
    max: Math.max(...timeMetaRaw.map(t => t.id)),
    fromYear(year:number) {
        for(let t of timeMetaRaw) {
            if(year && t.start<=year && t.end>year) {
                return t.id;
            }
        }
        throw new Error(`No time entry found for year ${year}`);
    }
};
export default timeMeta;
