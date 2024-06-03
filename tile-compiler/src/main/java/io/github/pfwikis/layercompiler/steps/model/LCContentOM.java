package io.github.pfwikis.layercompiler.steps.model;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import com.fasterxml.jackson.databind.util.TokenBuffer;

import lombok.AllArgsConstructor;
import lombok.SneakyThrows;

@AllArgsConstructor
public class LCContentOM<T> extends LCContent {
	private final Class<T> type;
	private T val;

	@Override
	@SneakyThrows
	public byte[] toBytes() {
		checkValidUsage();
		var res = MAPPER.writeValueAsBytes(val);
		return res;
	}

	@Override
	public InputStream toInputStream() {
		return new ByteArrayInputStream(toBytes());
	}

	@Override
	@SneakyThrows
	public <R> R toParsed(Class<R> cl) {
		checkValidUsage();
		TokenBuffer tb = new TokenBuffer(MAPPER.getFactory().getCodec(), false);
		MAPPER.writeValue(tb, val);
		var res = MAPPER.readValue(tb.asParser(), cl);
		return res;
	}
	
	@Override
	protected void cleanup() {
		val = null;
	}
}