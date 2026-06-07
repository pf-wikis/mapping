package io.github.pfwikis.run;

import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import org.apache.commons.lang3.Strings;
import org.slf4j.event.Level;

import io.github.pfwikis.layercompiler.steps.model.StepExecutor;
import io.github.pfwikis.layercompiler.steps.model.data.GeoData;
import io.github.pfwikis.run.Runner.OutFile;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Getter
@Slf4j
public class StdHelper implements Closeable {

	private final StepExecutor step;
	private final File file;
	private final GeoData content;
	private String alreadyPrinted = "";
	private String alreadyPrintedButPotential = "";
	private String prefix;

	public StdHelper(String prefix, StepExecutor step) {
		this.prefix = prefix;
		this.step = step;
		this.file = Runner.tmpGeojson(step, new OutFile());
		this.content = GeoData.from(file);
	}

	public void intermediatePrint() {
		try {
			var ct = content.toRawString().stripTrailing();
			
			//remove deleted lines
			ct = ct.replaceAll("[^\n]*\r", "");
			String potential;
			if(ct.contains("\n")) {
				int i = ct.lastIndexOf('\n');
				potential=ct.substring(i);
				ct = ct.substring(0,i);
			}
			else {
				potential = ct;
				ct = "";
			}
			
			var toPrint = ct+potential;
			toPrint = Strings.CS.removeStart(toPrint, alreadyPrinted);
			toPrint = Strings.CS.removeStart(toPrint, alreadyPrintedButPotential);
			
			if(!toPrint.isBlank()) {
				log(Level.INFO, toPrint);
				
			}
			
			alreadyPrinted = ct;
			alreadyPrintedButPotential = potential;
		} catch(Exception e) {
			if(e instanceof FileNotFoundException || e.getCause() instanceof FileNotFoundException)
				return; //we can ignore this case
			log.error("Could not print", e);
		}
	}

	private void log(Level level, String msg) {
		log.atLevel(level).log(
			"{}|{}:\n{}",
			step==null?"no step":step.getDescription().getId(),
			prefix,
			msg
		);
	}

	@Override
	public void close() throws IOException {
	}
}
