package io.github.pfwikis.layercompiler.steps.model;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public class LCContentBytes extends LCContent {
	private byte[] bytes;

	@Override
	public InputStream toInputStream() {
		checkValidUsage();
		return new ByteArrayInputStream(bytes);
	}

	@Override
	public byte[] toBytes() {
		checkValidUsage();
		return bytes;
	}
	
	@Override
	protected void cleanup() {
		bytes = null;
	}
}