package io.github.pfwikis.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class TimingStatsTest {

    @Test
    void aggregatesWorkAndWallTime() {
        var start = Instant.parse("2026-01-01T00:00:00Z");
        var first = TimingStats.of(Duration.ofSeconds(2), start, start.plusSeconds(5));
        var second = TimingStats.of(Duration.ofSeconds(3), start.plusSeconds(1), start.plusSeconds(8));

        var result = first.plus(second);

        assertThat(result.work()).isEqualTo(Duration.ofSeconds(5));
        assertThat(result.totalWall()).isEqualTo(Duration.ofSeconds(8));
    }
}
