package io.github.pfwikis.layercompiler.steps.model;

import java.io.InputStream;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class LCContentEmpty extends LCContent {
	public static LCContent INSTANCE = new LCContentEmpty();
	
	@Override
	public InputStream toInputStream() {
		throw new IllegalStateException("Called a transformation on empty content "+name);
	}
	
	@Override
	protected synchronized void checkValidUsage() {
		throw new IllegalStateException("Called a transformation on empty content "+name);
	}
}