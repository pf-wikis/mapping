package io.github.pfwikis.run;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.event.Level;

import io.github.pfwikis.layercompiler.steps.model.LCContent;
import io.github.pfwikis.layercompiler.steps.model.LCStep;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Getter
@Slf4j
public class StdHelper implements Closeable {

	private final LCStep step;
	private final File file;
	private final LCContent content;
	private String alreadyPrinted = "";
	private String prefix;

	public StdHelper(String prefix, LCStep step) {
		this.prefix = prefix;
		this.step = step;
		this.file = Runner.tmpGeojson(step);
		this.content = LCContent.from(file, true);
	}

	public void intermediatePrint() {
		var ct = content.toRawString();
		//unify linebreaks
		ct = ct.replaceAll("[\r\n]+", "\n");
		if(ct.contains("\n"))
			ct = ct.substring(0,ct.lastIndexOf('\n'));
		else
			ct = "";
		
		var toPrint = StringUtils.removeStart(ct, alreadyPrinted);
		
		if(!toPrint.isBlank()) {
			log(Level.INFO, toPrint);
			
		}
		
		alreadyPrinted = ct;
	}

	private void log(Level level, String msg) {
		log.atLevel(level).log(
			"{}|{}:\n\t{}",
			step==null?"no step":step.getName(),
			prefix,
			msg.replace("\n", "\n\t")
		);
	}

	@Override
	public void close() throws IOException {
		content.finishUsage();
	}

}
