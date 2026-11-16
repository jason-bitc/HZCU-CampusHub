package cn.edu.hzcu.ops;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

import static cn.edu.hzcu.ops.OpsRecoveryEvidence.TargetStatus.*;
import static org.assertj.core.api.Assertions.assertThat;

class OpsRecoveryEvidenceTest {
    private static final Instant START = Instant.parse("2026-11-16T08:00:00Z");

    @Test void bothTargetsIncludeTheirExactThreshold() {
        var elapsed = Duration.ofMinutes(10);
        var evidence = OpsRecoveryEvidence.measure(START.minus(Duration.ofHours(24)), START, START.plus(elapsed), elapsed);

        assertThat(evidence.snapshotAgeMillis()).isEqualTo(86_400_000L);
        assertThat(evidence.restoreElapsedMillis()).isEqualTo(600_000L);
        assertThat(evidence.rpoTargetStatus()).isEqualTo(WITHIN_TARGET);
        assertThat(evidence.rtoTargetStatus()).isEqualTo(WITHIN_TARGET);
    }

    @Test void elapsedDurationUsesTheMonotonicMeasurementEvenIfTheWallClockChanges() {
        var elapsed = Duration.ofMinutes(10).plusMillis(1);
        var evidence = OpsRecoveryEvidence.measure(START.minus(Duration.ofHours(24)).minusMillis(1),
                START, START.minusSeconds(1), elapsed);

        assertThat(evidence.snapshotAgeMillis()).isEqualTo(86_400_001L);
        assertThat(evidence.restoreElapsedMillis()).isEqualTo(600_001L);
        assertThat(evidence.rpoTargetStatus()).isEqualTo(EXCEEDS_TARGET);
        assertThat(evidence.rtoTargetStatus()).isEqualTo(EXCEEDS_TARGET);
    }

    @Test void missingAndFutureSnapshotTimesDoNotInventRpoMeasurements() {
        var elapsed = Duration.ofSeconds(1);
        var legacy = OpsRecoveryEvidence.measure(null, START, START.plus(elapsed), elapsed);
        var future = OpsRecoveryEvidence.measure(START.plusSeconds(1), START, START.plus(elapsed), elapsed);

        assertThat(legacy.backupSnapshotAt()).isNull();
        assertThat(legacy.snapshotAgeMillis()).isNull();
        assertThat(legacy.rpoTargetStatus()).isEqualTo(UNAVAILABLE);
        assertThat(future.snapshotAgeMillis()).isNull();
        assertThat(future.rpoTargetStatus()).isEqualTo(UNAVAILABLE);
        assertThat(legacy.rtoTargetStatus()).isEqualTo(WITHIN_TARGET);
    }
}
