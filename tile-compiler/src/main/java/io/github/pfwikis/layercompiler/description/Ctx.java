package io.github.pfwikis.layercompiler.description;

import io.github.pfwikis.CLIOptions;
import lombok.Getter;
import lombok.Setter;

@Getter
public enum Ctx {
	
	INSTANCE;
	
	@Setter
    private CLIOptions options;
}