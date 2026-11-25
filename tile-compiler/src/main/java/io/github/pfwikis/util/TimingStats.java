package io.github.pfwikis.util;

import java.time.Duration;
import java.time.Instant;

import com.google.common.collect.Range;
import com.google.common.collect.RangeSet;
import com.google.common.collect.TreeRangeSet;

/**
 * Aggregated timing information for one step or sub-step.
 *
 * <p>{@code work} is the sum of the individual task durations. {@code wall}
 * is the elapsed time from the first task start to the last task end. They are
 * equal for sequential work and differ when tasks run in parallel.</p>
 */
public record TimingStats(Duration work, RangeSet<Instant> wall) {

    public static TimingStats empty() {
        return new TimingStats(Duration.ZERO, TreeRangeSet.create());
    }

    public static TimingStats of(Duration work, Instant start, Instant end) {
    	var wall = TreeRangeSet.<Instant>create();
    	wall.add(Range.closedOpen(start, end));
        return new TimingStats(work, wall);
    }

    public TimingStats plus(TimingStats other) {
    	var wall = TreeRangeSet.create(this.wall);
    	wall.addAll(other.wall);
        return new TimingStats(
            work.plus(other.work),
            wall
        );
    }

    public Duration totalWall() {
    	return wall.asRanges().stream().map(r->Duration.between(r.lowerEndpoint(), r.upperEndpoint())).reduce(Duration::plus).orElse(Duration.ZERO);
    }
}
