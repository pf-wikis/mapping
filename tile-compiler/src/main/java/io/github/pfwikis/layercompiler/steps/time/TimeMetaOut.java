package io.github.pfwikis.layercompiler.steps.time;

import java.io.IOException;
import java.nio.file.Files;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.github.pfwikis.layercompiler.description.Ctx;
import io.github.pfwikis.layercompiler.steps.model.Inputs;
import io.github.pfwikis.layercompiler.steps.model.StepExecutor;
import io.github.pfwikis.layercompiler.steps.model.Time;
import io.github.pfwikis.layercompiler.steps.model.content.Content;
import io.github.pfwikis.util.Jackson;
import io.github.pfwikis.util.time.TimeRange;

@Time.Requirement(Time.Requirement.Value.REQUIRES_MERGED)
public class TimeMetaOut extends StepExecutor {

	@Override
	public Content process(Inputs in) throws IOException {
		var meta = in.getInput("meta").toFeatureCollection().getProperties().getTimeMeta();
		var res = meta.getEntries()
			.stream()
			.map(e->new Res(
				e.getId(),
				toLabel(e.getTime()),
				e.getTime().hasLowerBound()?e.getTime().getTimeStart():(e.getTime().getTimeEnd()-1),
				e.getTime().hasLowerBound()?e.getTime().getTimeStart():null,
				e.getTime().hasUpperBound()?e.getTime().getTimeEnd():null
			))
			.toList();
		int offset = -res.stream().mapToInt(r->r.id).min().getAsInt();
		var pp = Jackson.JSON.writerWithDefaultPrettyPrinter();

		var sb = """
			const oldest = %s as const;
			const latest = %s as const;
			const data = [oldest,...%s,latest] as const;
			export type TimeSlice = typeof data[number];
			export default {
				byId(id:number):TimeSlice {
					let index = id + %s;
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
			""".formatted(
				pp.writeValueAsString(res.getFirst()),
				pp.writeValueAsString(res.getLast()),
				pp.writeValueAsString(res.subList(1, res.size()-1)),
				offset
			);
		
		Ctx.INSTANCE.getOptions().targetGenDirectory().mkdirs();
		Files.writeString(
			Ctx.INSTANCE.getOptions().targetGenDirectory().toPath().resolve("timeMeta.ts"),
			sb
		);
		
		return Content.empty();
	}
	
	private String toLabel(TimeRange time) {
		if(!time.hasLowerBound() && !time.hasUpperBound()) {
			throw new IllegalStateException();
		}
		if(!time.hasLowerBound())
			return "before <a href=\"https://pathfinderwiki.com/wiki/%1$d_AR\">%1$d AR</a>"
					.formatted(time.getTimeEnd());
		if(!time.hasUpperBound())
			return "since <a href=\"https://pathfinderwiki.com/wiki/%1$d_AR\">%1$d AR</a>"
					.formatted(time.getTimeStart());
		
		int start = time.getTimeStart();
		int end = time.getTimeEnd()-1;
		if(start == end)
			return "<a href=\"https://pathfinderwiki.com/wiki/%1$d_AR\">%1$d AR</a>"
					.formatted(start);
		
		return "<a href=\"https://pathfinderwiki.com/wiki/%1$d_AR\">%1$d AR</a> - <a href=\"https://pathfinderwiki.com/wiki/%2$d_AR\">%2$d AR</a>"
				.formatted(start, end);
	}

	private static record Res(
		int id,
		String label,
		Integer representativeYear,
		@JsonInclude(JsonInclude.Include.ALWAYS)
		Integer start,
		@JsonInclude(JsonInclude.Include.ALWAYS)
		Integer end
	) {}
}
