package io.github.pfwikis.util;

import java.time.Duration;
import java.time.Instant;

/**
 * Aggregated timing information for one step or sub-step.
 *
 * <p>{@code work} is the sum of the individual task durations. {@code wall}
 * is the elapsed time from the first task start to the last task end. They are
 * equal for sequential work and differ when tasks run in parallel.</p>
 */
public record TimingStats(Duration work, Instant firstStart, Instant lastEnd) {

    public static TimingStats empty() {
        return new TimingStats(Duration.ZERO, Instant.MAX, Instant.MIN);
    }

    public static TimingStats of(Duration work, Instant start, Instant end) {
        return new TimingStats(work, start, end);
    }

    public TimingStats plus(TimingStats other) {
        return new TimingStats(
            work.plus(other.work),
            firstStart.isBefore(other.firstStart) ? firstStart : other.firstStart,
            lastEnd.isAfter(other.lastEnd) ? lastEnd : other.lastEnd
        );
    }

    public Duration wall() {
        return firstStart.equals(Instant.MAX) ? Duration.ZERO : Duration.between(firstStart, lastEnd);
    }
}
