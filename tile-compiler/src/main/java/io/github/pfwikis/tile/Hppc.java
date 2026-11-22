package io.github.pfwikis.tile;

import com.carrotsearch.hppc.IntObjectHashMap;

final class Hppc {
	private Hppc() {}

	static <T> IntObjectHashMap<T> newIntObjectHashMap() {
		return new IntObjectHashMap<>();
	}
}
