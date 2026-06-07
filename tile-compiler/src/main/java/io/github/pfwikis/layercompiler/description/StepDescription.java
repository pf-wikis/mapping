package io.github.pfwikis.layercompiler.description;

import io.github.pfwikis.layercompiler.steps.model.StepExecutor;
import io.github.pfwikis.layercompiler.steps.model.Time;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@Getter @Setter
@RequiredArgsConstructor
public class StepDescription {
	private final String id;
	private final String group;
	private final String step;
	private final StepExecutor executor;
	private Time.Requirement.Value timeRequirement;
}
