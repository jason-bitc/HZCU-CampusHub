package cn.edu.hzcu.ops;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

final class OpsDailyBackup implements AutoCloseable {
    static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final LocalTime time;
    private final Clock clock;
    private final OpsJobs jobs;
    private final BooleanSupplier ready;
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();

    OpsDailyBackup(LocalTime time, Clock clock, OpsJobs jobs, BooleanSupplier ready) {
        this.time = time;
        this.clock = clock;
        this.jobs = jobs;
        this.ready = ready;
    }

    void start() {
        timer.scheduleWithFixedDelay(this::poll, 0, 1, TimeUnit.MINUTES);
    }

    void tick() throws IOException {
        var now = ZonedDateTime.ofInstant(clock.instant(), ZONE);
        if (!now.toLocalTime().isBefore(time) && ready.getAsBoolean()) {
            jobs.scheduleBackup(now.toLocalDate());
        }
    }

    private void poll() {
        try {
            tick();
        } catch (IOException | RuntimeException failure) {
            System.out.println("event=OPS_DAILY_BACKUP_POLL_FAILED");
        }
    }

    @Override
    public void close() {
        timer.shutdownNow();
        try {
            if (!timer.awaitTermination(5, TimeUnit.SECONDS)) throw new IllegalStateException("OPS_BACKUP_TIMER_STILL_STOPPING");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("OPS_BACKUP_TIMER_STILL_STOPPING");
        }
    }
}
