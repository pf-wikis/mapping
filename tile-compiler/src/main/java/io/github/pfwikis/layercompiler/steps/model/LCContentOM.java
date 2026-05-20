package io.github.pfwikis.layercompiler.steps.model;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import com.fasterxml.jackson.databind.util.TokenBuffer;

import io.github.pfwikis.util.Jackson;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;

@AllArgsConstructor
public class LCContentOM<T> extends LCContent {
	private final Class<T> type;
	private T val;

	@Override
	@SneakyThrows
	public byte[] toBytes() {
		var res = Jackson.JSON.writeValueAsBytes(val);
		return res;
	}

	@Override
	public InputStream toInputStream() {
		return new ByteArrayInputStream(toBytes());
	}

	@Override
	@SneakyThrows
	public <R> R toParsed(Class<R> cl) {
		TokenBuffer tb = new TokenBuffer(Jackson.JSON.getFactory().getCodec(), false);
		Jackson.JSON.writeValue(tb, val);
		var res = Jackson.JSON.readValue(tb.asParser(), cl);
		return res;
	}
	
	@Override
	protected void cleanup() {
		val = null;
	}
}