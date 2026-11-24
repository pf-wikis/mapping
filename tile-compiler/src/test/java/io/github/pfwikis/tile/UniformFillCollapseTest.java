package io.github.pfwikis.tile;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.pfwikis.tile.TileGeometryCodec.GeometryType;

class UniformFillCollapseTest {

	private final TileContext context = new TileContext();

	@Test
	void keepsOnlyTheTopmostOpaqueFill() {
		var red = fill(0, 0, "#FF0000");
		var green = fill(0, 1, "#00FF00");
		var blue = fill(0, 2, "#0000FF");

		assertThat(MltPmtilesCompiler.collapseUniformFills(List.of(red, green, blue)))
			.containsExactly(blue);
	}

	@Test
	void collapsesEvenIfAColorHasAlpha() {
		// Features are opaque by contract, so alpha is ignored here.
		var red = fill(0, 0, "#FF0000");
		var translucent = fill(0, 1, "#0000FF80");

		assertThat(MltPmtilesCompiler.collapseUniformFills(List.of(red, translucent)))
			.containsExactly(translucent);
	}

	@Test
	void keepsTheStackWhenABoundaryFeatureIsPresent() {
		var bottom = fill(0, 0, "#FF0000");
		var top = fill(0, 1, "#0000FF");
		var line = new TileStore.TileFeature(0, 2, attrs("#FFFFFF"), GeometryType.LINE, new int[0], false);

		assertThat(MltPmtilesCompiler.collapseUniformFills(List.of(bottom, top, line)))
			.containsExactly(bottom, top, line);
	}

	@Test
	void keepsTheStackAcrossDifferentLayers() {
		var bottom = fill(0, 0, "#FF0000");
		var top = fill(1, 0, "#0000FF");

		assertThat(MltPmtilesCompiler.collapseUniformFills(List.of(bottom, top)))
			.containsExactly(bottom, top);
	}

	private TileStore.TileFeature fill(int layer, long order, String color) {
		return new TileStore.TileFeature(layer, order, attrs(color), GeometryType.POLYGON, new int[0], true);
	}

	private int attrs(String color) {
		return context.attrsId(Map.of("color", color));
	}
}
