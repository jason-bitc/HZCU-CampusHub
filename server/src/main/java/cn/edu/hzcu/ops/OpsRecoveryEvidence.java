package cn.edu.hzcu.ops;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

record OpsRecoveryEvidence(String backupSnapshotAt, String restoreStartedAt, String serviceAvailableAt,
                           Long snapshotAgeMillis, long restoreElapsedMillis,
                           long rpoTargetMillis, long rtoTargetMillis,
                           TargetStatus rpoTargetStatus, TargetStatus rtoTargetStatus) {
    private static final Duration RPO_TARGET = Duration.ofHours(24);
    private static final Duration RTO_TARGET = Duration.ofMinutes(10);
    private static final Duration MAX_MILLIS = Duration.ofMillis(Long.MAX_VALUE);
    enum TargetStatus { WITHIN_TARGET, EXCEEDS_TARGET, UNAVAILABLE }

    static OpsRecoveryEvidence measure(Instant snapshotAt, Instant restoreStartedAt, Instant serviceAvailableAt,
                                       Duration restoreElapsed) {
        Duration snapshotAge = snapshotAt == null ? null : Duration.between(snapshotAt, restoreStartedAt);
        if (snapshotAge != null && (snapshotAge.isNegative() || snapshotAge.compareTo(MAX_MILLIS) > 0)) {
            snapshotAge = null;
        }
        return new OpsRecoveryEvidence(Objects.toString(snapshotAt, null), restoreStartedAt.toString(),
                serviceAvailableAt.toString(), snapshotAge == null ? null : snapshotAge.toMillis(),
                restoreElapsed.toMillis(), RPO_TARGET.toMillis(), RTO_TARGET.toMillis(),
                compare(snapshotAge, RPO_TARGET), compare(restoreElapsed, RTO_TARGET));
    }

    private static TargetStatus compare(Duration measured, Duration target) {
        if (measured == null) return TargetStatus.UNAVAILABLE;
        return measured.compareTo(target) <= 0 ? TargetStatus.WITHIN_TARGET : TargetStatus.EXCEEDS_TARGET;
    }
}
