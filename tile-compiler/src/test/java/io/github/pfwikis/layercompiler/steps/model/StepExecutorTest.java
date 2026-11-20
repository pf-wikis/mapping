package io.github.pfwikis.layercompiler.steps.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.pfwikis.layercompiler.steps.model.content.TimeSlicedContent.TimeSlice;
import io.github.pfwikis.layercompiler.steps.model.data.GeoData;
import io.github.pfwikis.model.Feature;
import io.github.pfwikis.model.FeatureCollection;
import io.github.pfwikis.util.time.TimeRange;

class StepExecutorTest {

	@Test
	void mergesAdjacentIdenticalSlices() {
		var first = data("same");
		var second = data("same");

		var result = StepExecutor.mergeIdenticalSlices(slices(
			new TimeSlice(time(null, 10), first),
			new TimeSlice(time(10, 20), second)
		));

		assertThat(result).hasSize(1);
		assertThat(result.getFirst().getTime()).isEqualTo(time(null, 20));
		assertThat(result.getFirst().getData()).isSameAs(first);
	}

	@Test
	void mergesChainsOfIdenticalSlicesAfterSorting() {
		var data = data("same");

		var result = StepExecutor.mergeIdenticalSlices(slices(
			new TimeSlice(time(10, 20), data),
			new TimeSlice(time(null, 10), data),
			new TimeSlice(time(20, 30), data)
		));

		assertThat(result).hasSize(1);
		assertThat(result.getFirst().getTime()).isEqualTo(time(null, 30));
		assertThat(result.getFirst().getData()).isSameAs(data);
	}

	@Test
	void doesNotMergeIdenticalSlicesWithAGap() {
		var first = data("same");
		var second = data("same");

		var result = StepExecutor.mergeIdenticalSlices(slices(
			new TimeSlice(time(null, 10), first),
			new TimeSlice(time(20, 30), second)
		));

		assertThat(result).hasSize(2);
		assertThat(result.get(0).getTime()).isEqualTo(time(null, 10));
		assertThat(result.get(1).getTime()).isEqualTo(time(20, 30));
	}

	@Test
	void doesNotMergeAdjacentSlicesWithDifferentData() {
		var result = StepExecutor.mergeIdenticalSlices(slices(
			new TimeSlice(time(null, 10), data("first")),
			new TimeSlice(time(10, 20), data("second"))
		));

		assertThat(result).hasSize(2);
	}

	@Test
	void mergesAdjacentEmptySlices() {
		var result = StepExecutor.mergeIdenticalSlices(slices(
			new TimeSlice(time(null, 10), GeoData.empty()),
			new TimeSlice(time(10, 20), GeoData.empty())
		));

		assertThat(result).hasSize(1);
		assertThat(result.getFirst().getTime()).isEqualTo(time(null, 20));
	}

	private static GeoData data(String text) {
		var feature = new Feature();
		feature.getProperties().setText(text);
		var collection = new FeatureCollection();
		collection.getFeatures().add(feature);
		return GeoData.from(collection);
	}

	private static TimeRange time(Integer start, Integer end) {
		return new TimeRange(start, end);
	}

	private static ArrayList<TimeSlice> slices(TimeSlice... slices) {
		return new ArrayList<>(List.of(slices));
	}
}
