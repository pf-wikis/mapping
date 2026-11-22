package io.github.pfwikis.tile;

final class Format {
	private static final Format INSTANCE = new Format();

	static Format defaultInstance() {
		return INSTANCE;
	}

	String integer(long value) {
		return Long.toString(value);
	}

	String storage(long bytes) {
		return bytes+"B";
	}
}
