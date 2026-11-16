package cn.edu.hzcu.ops;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpsBackupExportTest {
    @TempDir Path root;

    @Test void retentionUsesCreationTimeAndExcludesIncompleteManifests() throws Exception {
        var config = config();
        var base = Instant.parse("2030-01-01T00:00:00Z");
        for (int i = 0; i < 9; i++) snapshot(config, "backup-" + (9 - i), base.plus(i, ChronoUnit.DAYS), true);
        snapshot(config, "incomplete", base.plus(20, ChronoUnit.DAYS), false);

        var entries = new OpsRunner(config).backups();
        assertThat(entries).extracting(OpsRunner.BackupEntry::id)
                .containsExactly("backup-1", "backup-2", "backup-3", "backup-4", "backup-5", "backup-6",
                        "backup-7", "backup-8", "backup-9");
        assertThat(entries.stream().filter(OpsRunner.BackupEntry::withinRetentionWindow).toList()).hasSize(7);
        assertThat(config.backupRoot().resolve("backup-9/manifest.json")).exists();
    }

    @Test void exportIsVerifiableWithoutDatabaseAndNeverOverwritesACompletedCopy() throws Exception {
        var config = config();
        var source = snapshot(config, "latest", Instant.parse("2030-01-01T00:00:00Z"), true);
        var destination = Files.createDirectory(root.resolve("handoff"));

        var receipt = OpsBackupExport.export(config, "latest", destination);
        Path copy = destination.resolve("latest");
        assertThat(receipt.manifestSha256()).isEqualTo(OpsRunner.sha(source.resolve("manifest.json")));
        assertThat(receipt.verifiedFileCount()).isEqualTo(1);
        assertThat(copy.resolve("media")).isDirectory();
        assertThat(OpsBackupExport.verify(copy, receipt.manifestSha256()).manifestSha256())
                .isEqualTo(receipt.manifestSha256());
        assertThatThrownBy(() -> OpsBackupExport.verify(copy, "0".repeat(64))).hasMessage("OPS_BACKUP_CHANGED");
        assertThatThrownBy(() -> OpsBackupExport.export(config, "latest", destination)).hasMessage("OPS_EXPORT_EXISTS");
        assertThatThrownBy(() -> OpsBackupExport.export(config, "latest", config.backupRoot()))
                .hasMessage("OPS_EXPORT_ROOT_OVERLAP");

        Files.writeString(copy.resolve("database.sql"), "corrupt");
        assertThatThrownBy(() -> OpsBackupExport.verify(copy, receipt.manifestSha256())).hasMessage("OPS_BACKUP_INVALID");
        assertThat(Files.readString(source.resolve("database.sql"))).isEqualTo("-- fixture");
    }

    @Test void invalidSourceDoesNotExposeAnExport() throws Exception {
        var config = config();
        var source = snapshot(config, "latest", Instant.parse("2030-01-01T00:00:00Z"), true);
        var destination = Files.createDirectory(root.resolve("handoff"));
        Files.writeString(source.resolve("database.sql"), "corrupt");

        assertThatThrownBy(() -> OpsBackupExport.export(config, "latest", destination)).hasMessage("OPS_BACKUP_INVALID");
        try (var contents = Files.list(destination)) {
            assertThat(contents.toList()).isEmpty();
        }
    }

    @Test void legacyManifestIsAcceptedButMalformedBusinessSnapshotIsRejected() throws Exception {
        var config = config();
        var source = snapshot(config, "latest", Instant.parse("2030-01-01T00:00:00Z"), true);
        var legacy = OpsRunner.readBackupManifest(source, "latest", null);
        assertThat(OpsRunner.verifyBackupFiles(source, "latest", legacy).preview().businessSnapshotAvailable()).isFalse();
        assertThat(OpsBusinessSnapshot.verify(null, null).status()).isEqualTo("UNAVAILABLE");

        var manifest = (tools.jackson.databind.node.ObjectNode) legacy.data();
        manifest.putNull("businessSnapshot");
        Files.write(source.resolve("manifest.json"), OpsStore.JSON.writeValueAsBytes(manifest));
        var malformed = OpsRunner.readBackupManifest(source, "latest", null);
        assertThatThrownBy(() -> OpsRunner.verifyBackupFiles(source, "latest", malformed)).hasMessage("OPS_BACKUP_INVALID");
    }

    private OpsConfig config() {
        return new OpsConfig(root.resolve("control"), root.resolve("runtime"), root.resolve("artifacts"),
                root.resolve("backups"), root.resolve("media"), Path.of("unused"), Path.of("unused"),
                Path.of("unused"), root.resolve("missing-env"), 8091, "http://127.0.0.1:8080/actuator/health",
                "http://127.0.0.1:5173", "campushub_test", null);
    }

    private Path snapshot(OpsConfig config, String id, Instant time, boolean complete) throws Exception {
        Path directory = config.backupRoot().resolve(id);
        Files.createDirectories(directory.resolve("media"));
        Path dump = Files.writeString(directory.resolve("database.sql"), "-- fixture");
        Files.write(directory.resolve("manifest.json"), OpsStore.JSON.writeValueAsBytes(Map.of(
                "id", id, "createdAt", time.toString(), "complete", complete, "database", config.databaseName(),
                "schemaFingerprint", "a".repeat(64), "files", Map.of("database.sql", OpsRunner.sha(dump)))));
        return directory;
    }
}
