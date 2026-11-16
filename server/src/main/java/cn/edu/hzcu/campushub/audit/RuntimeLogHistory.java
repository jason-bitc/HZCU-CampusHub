package cn.edu.hzcu.campushub.audit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

/** Protects runtime log segments displaced by the size-based rotation. */
final class RuntimeLogHistory {
    private static final Duration RETENTION = Duration.ofDays(30);
    private static final Pattern ARCHIVE = Pattern.compile(
            "campushub-runtime-([0-9]{1,19})-[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}\\.log");
    private final Path directory;
    private final Clock clock;
    private Instant cleanupAfter = Instant.MIN;

    RuntimeLogHistory(Path logRoot, Clock clock) {
        directory = logRoot.resolve("runtime-retained");
        this.clock = clock;
    }

    void validate() throws IOException {
        if (!Files.notExists(directory, LinkOption.NOFOLLOW_LINKS)
                && (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS) || !directory.equals(directory.toRealPath()))) {
            throw new IOException("RUNTIME_LOG_HISTORY_UNAVAILABLE");
        }
    }

    void preserve(Path oldest) throws IOException {
        validate();
        Files.createDirectories(directory);
        validate();
        Instant now = clock.instant();
        String name = "campushub-runtime-" + now.toEpochMilli() + "-" + UUID.randomUUID() + ".log";
        Files.move(oldest, directory.resolve(name));
        cleanup();
    }

    void cleanup() {
        Instant now = clock.instant();
        if (now.isBefore(cleanupAfter)) return;
        cleanupAfter = now.plus(Duration.ofDays(1));
        try {
            validate();
            if (Files.notExists(directory, LinkOption.NOFOLLOW_LINKS)) return;
            Instant cutoff = now.minus(RETENTION);
            try (var files = Files.newDirectoryStream(directory, "campushub-runtime-*.log")) {
                for (Path file : files) {
                    if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) continue;
                    var name = ARCHIVE.matcher(file.getFileName().toString());
                    if (!name.matches()) continue;
                    Instant archivedAt;
                    try { archivedAt = Instant.ofEpochMilli(Long.parseLong(name.group(1))); }
                    catch (NumberFormatException invalid) { continue; }
                    if (!archivedAt.isAfter(cutoff)
                            && !Files.getLastModifiedTime(file, LinkOption.NOFOLLOW_LINKS).toInstant().isAfter(cutoff)) {
                        Files.deleteIfExists(file);
                    }
                }
            }
        } catch (IOException | java.nio.file.DirectoryIteratorException failure) {
            System.err.println("RUNTIME_LOG_RETENTION_CLEANUP_UNAVAILABLE");
        }
    }
}
