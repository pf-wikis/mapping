package io.github.pfwikis.layercompiler.steps;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import io.github.pfwikis.layercompiler.steps.model.LCContent;
import io.github.pfwikis.layercompiler.steps.model.LCContentEmpty;
import io.github.pfwikis.layercompiler.steps.model.LCStep;
import io.github.pfwikis.run.Tools;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CheckGeometry extends LCStep {
	
	@Setter
	private String layer;

	@Override
	public LCContent process() throws Exception {
		if(layer == null) return LCContentEmpty.INSTANCE; //cities and locations
		
		var in = Tools.mapshaper(this, getInput(), "id-field=fid");
		
		var errors = new ArrayList<String>();

		//FIXME suddenly need a project file?
		/*

		
		test(in, errors, "ERRORS", "native:checkgeometrymissingvertex");
		test(in, errors, "ERRORS", "native:checkgeometrydegeneratepolygon");
		test(in, errors, "ERRORS", "native:checkgeometryduplicate");
		test(in, errors, "ERRORS", "native:checkgeometryduplicatenodes");
		if(!"labels".equals(layer)) {
			test(in, errors, "ERRORS", "native:checkgeometryoverlap", "--MIN_OVERLAP_AREA=0");
		}
		test(in, errors, "ERRORS", "native:checkgeometryselfcontact");
		test(in, errors, "ERRORS", "native:checkgeometryselfintersection");
		//test(in, errors, "ERRORS", "native:checkgeometrysliverpolygon", "--MAX_THINNESS=20", "--MAX_AREA=1000000");
		//test(in, errors, "ERRORS", "native:checkgeometrygap", "--GAP_THRESHOLD=0", "--NEIGHBORS=TEMPORARY_OUTPUT", "--OUTPUT=TEMPORARY_OUTPUT");
		test(in, errors, "ERRORS", "native:checkgeometrylineintersection");
		test(in, errors, "NULL_OUTPUT", "native:removenullgeometries", "--REMOVE_EMPTY=true");
		*/	
		in.finishUsage();
		
		if(!errors.isEmpty()) {
			var sb = new StringBuilder();
			sb.append("Failed geometry check for layer '").append(layer).append("':\n");
			for(var e:errors) {
				sb.append(e).append("\n");
			}
			throw new IllegalArgumentException(sb.toString());
		}
		
		return LCContentEmpty.INSTANCE;
	}
	
	private void test(LCContent in, List<String> errors, String outputName, String cmd, String... args) throws IOException {	
		var result = Tools.qgis(
			this,
			cmd,
			outputName,
			in,
			args,
			"--TOLERANCE=8",
			"--UNIQUE_ID=fid"
		);
		var res = result.toFeatureCollectionAndFinish();
		var localErrors = new ArrayList<String>();
		if(res.getFeatures().size()==0) return;
		for(var fail:res.getFeatures()) {
			var sb = new StringBuilder();
			if(fail.getProperties().getFid() != null)
				sb.append("fid: ").append(fail.getProperties().getFid()).append(" ");
			if(fail.getProperties().getGc_errorx() != null) {
				sb.append("at ").append(fail.getProperties().getGc_errory()).append(",").append(fail.getProperties().getGc_errorx());
			}
			else if(fail.getGeometry() != null)
				sb.append("roughly at ").append(fail.getGeometry().streamPoints().findFirst().get().toString());
			localErrors.add(sb.toString());
		}
		if(!localErrors.isEmpty())
			errors.add("  "+cmd+"\n"+localErrors.stream().map(e->"    - "+e).collect(Collectors.joining("\n")));
	}
}
