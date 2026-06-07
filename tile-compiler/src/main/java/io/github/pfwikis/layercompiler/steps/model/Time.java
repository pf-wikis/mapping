package io.github.pfwikis.layercompiler.steps.model;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

public class Time {
	
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.TYPE)
	public @interface Requirement {
		public Value value();
		
		public static enum Value {
			REQUIRES_MERGED,
			ANY,
			REQUIRES_SLICED;
		}
	}
	
	public static enum DataState {
		MERGED {
			@Override
			public String mapshaperTimeFields() {
				return ",timeStart,timeEnd";
			}
		},
		TIMELESS;

		public String mapshaperTimeFields() {
			return "";
		}
	}
	
	public static enum ContentState {
		SLICED,
		MERGED,
		TIMELESS;
	}
	
	public static enum Action {
		SLICE_BEFORE_PROCESS,
		KEEP,
		MERGE_BEFORE_PROCESS;
	}
}