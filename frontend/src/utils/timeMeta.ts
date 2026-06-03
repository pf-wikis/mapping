import timeMetaRaw from "../../gen/time-meta.json";

const clean = timeMetaRaw.map(t => {
    return {
        id: t.id,
        label: t.label,
        start: t.start??null,
        end: t.end??null
    };
});

const timeMeta = {
    byIdMap: new Map(clean.map(t => [t.id, t])),
    byId(id:number) {
        let result = this.byIdMap.get(id);
        if (!result)
            throw new Error(`No time entry found for id ${id}`);
        return result;
    },
    min: Math.min(...clean.map(t => t.id)),
    max: Math.max(...clean.map(t => t.id)),
    fromYear(year:number) {
        for(let t of clean) {
            if((t.start??-Infinity)<=year && (t.end??Infinity)>year) {
                return t.id;
            }
        }
        throw new Error(`No time entry found for year ${year}`);
    }
};
export default timeMeta;
