package cn.edu.hzcu.ops;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/** Copies an immutable snapshot without opening the control journal or connecting to MySQL. */
final class OpsBackupExport {
    record Receipt(String backupId, String manifestSha256, String backupCreatedAt,
                   String verifiedAt, int verifiedFileCount) {}

    static Receipt export(OpsConfig config, String id, Path destination) throws IOException {
        Path outside = existingDirectory(destination);
        for (Path root : List.of(config.controlRoot(), config.runtimeRoot(), config.artifactRoot(),
                config.backupRoot(), config.mediaRoot())) {
            if (outside.startsWith(root) || root.startsWith(outside)) {
                throw new IOException("OPS_EXPORT_ROOT_OVERLAP");
            }
        }
        Path target = OpsRunner.child(outside, id);
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) throw new IOException("OPS_EXPORT_EXISTS");

        Path source = OpsRunner.child(config.backupRoot(), id);
        var manifest = OpsRunner.readBackupManifest(source, id, null);
        if (!config.databaseName().equals(manifest.data().path("database").asString())) {
            throw OpsRunner.failure("OPS_BACKUP_INCOMPATIBLE");
        }
        OpsRunner.verifyBackupFiles(source, id, manifest);

        Path staging = Files.createTempDirectory(outside, ".export-partial-");
        try {
            Files.createDirectory(staging.resolve("media"));
            for (var file : manifest.data().path("files").properties()) {
                copyFile(source, staging, file.getKey());
            }
            copyFile(source, staging, "manifest.json");
            Receipt receipt = verify(staging, id, manifest.sha256());
            Files.move(staging, target); // Fail if the final directory already exists.
            return receipt;
        } catch (IOException | RuntimeException failure) {
            try (var files = Files.walk(staging)) {
                for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(file);
            } catch (IOException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    static Receipt verify(Path copy, String expectedHash) throws IOException {
        Path folder = existingDirectory(copy);
        return verify(folder, folder.getFileName().toString(), expectedHash);
    }

    private static Receipt verify(Path folder, String id, String expectedHash) throws IOException {
        if (!expectedHash.matches("[0-9a-f]{64}")) throw new IOException("OPS_MANIFEST_SHA256_INVALID");
        var manifest = OpsRunner.readBackupManifest(folder, id, expectedHash);
        var plan = OpsRunner.verifyBackupFiles(folder, id, manifest);
        return new Receipt(id, plan.manifestSha256(), plan.preview().backupCreatedAt(), Instant.now().toString(),
                plan.preview().verifiedFileCount());
    }

    private static Path existingDirectory(Path path) throws IOException {
        Path normalized = path.toAbsolutePath().normalize();
        OpsStore.rejectLinks(normalized);
        if (normalized.getFileName() == null || !Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS)
                || !normalized.toRealPath().equals(normalized)) {
            throw new IOException("OPS_EXPORT_DIRECTORY_INVALID");
        }
        return normalized;
    }

    private static void copyFile(Path source, Path staging, String name) throws IOException {
        Path original = source.resolve(name);
        OpsStore.rejectLinks(original);
        if (!Files.isRegularFile(original, LinkOption.NOFOLLOW_LINKS) || !original.toRealPath().equals(original)) {
            throw OpsRunner.failure("OPS_BACKUP_INVALID");
        }
        Path copy = staging.resolve(name);
        Files.createDirectories(copy.getParent());
        Files.copy(original, copy);
    }
}
