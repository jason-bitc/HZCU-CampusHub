package cn.edu.hzcu.ops;

import java.nio.file.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Duration;
import java.net.*;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import cn.edu.hzcu.campushub.auth.AuthTestSupport.MutableClock;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static org.assertj.core.api.Assertions.*;

class OpsControlTest {
    @TempDir Path root;
    @Test void confirmationsAreSessionBoundMutationsAreExclusiveAndKeysAreDurable()throws Exception {
        var calls=new AtomicInteger();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);String key=UUID.randomUUID().toString(),id;
        try(var store=new OpsStore(root);var jobs=new OpsJobs(store,(a,r,h,p)->{calls.incrementAndGet();entered.countDown();release.await();p.accept("VERIFIED");return Map.of("done",true);})){
            String confirmation=(String)jobs.prepare("RESTART","business","","ops_test",null).get("confirmationId");
            assertThatThrownBy(()->jobs.submit("RESTART","business","",key,confirmation,"wrong")).isInstanceOf(OpsApplication.Failure.class);
            var first=jobs.submit("RESTART","business","",key,confirmation,"ops_test");id=first.path("id").asString();assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();
            assertThat(jobs.submit("RESTART","business","",key,"expired","other").path("id").asString()).isEqualTo(id);
            assertThatThrownBy(()->jobs.submit("BACKUP","business","",key,confirmation,"ops_test")).hasMessage("REQUEST_KEY_REUSED");
            var second=(String)jobs.prepare("BACKUP","business","","ops_test",null).get("confirmationId");assertThatThrownBy(()->jobs.submit("BACKUP","business","",UUID.randomUUID().toString(),second,"ops_test")).hasMessage("OPS_TARGET_BUSY");
            release.countDown();for(int i=0;i<100&&!jobs.get(id).path("state").asString().equals("SUCCEEDED");i++)Thread.sleep(20);
            assertThat(jobs.get(id).path("state").asString()).isEqualTo("SUCCEEDED");assertThat(calls.get()).isEqualTo(1);
        }finally{release.countDown();}
        try(var store=new OpsStore(root);var jobs=new OpsJobs(store,(a,r,h,p)->{throw new AssertionError("must not reexecute");})){
            assertThat(jobs.submit("RESTART","business","",key,"none","none").path("id").asString()).isEqualTo(id);
        }
    }
    @Test void interruptedJournalRequiresManualReconciliationAndNeverBecomesSuccess()throws Exception {
        try(var store=new OpsStore(root)){
            store.change(journal -> journal.withArray("jobs").addObject()
                    .put("id","test").put("action","RESTORE").put("state","RUNNING")
                    .put("protectionBackupId","safe-backup").putArray("events").addObject().put("phase","RESTORING"));
        }
        try(var store=new OpsStore(root);var jobs=new OpsJobs(store,(a,r,h,p)->Map.of())){
            assertThat(jobs.get("test").path("state").asString()).isEqualTo("INTERRUPTED");String c=(String)jobs.prepare("RESTART","business","","token",null).get("confirmationId");
            assertThat(jobs.get("test").path("finishedAt").asString()).isNotBlank();
            var recovery = jobs.recoveryStatus(store.read());
            assertThat(recovery.required()).isTrue();
            assertThat(recovery.incident()).isEqualTo(new OpsStore.RecoveryIncident(
                    "test","RESTORE","INTERRUPTED","EXECUTOR_INTERRUPTED","RESTORING","safe-backup"));
            assertThatThrownBy(()->jobs.submit("RESTART","business","",UUID.randomUUID().toString(),c,"token")).hasMessage("OPS_RECOVERY_REQUIRED");
            store.reconcile();
            assertThat(jobs.recoveryStatus(store.read())).isEqualTo(new OpsJobs.RecoveryStatus(false,null));
        }
        try(var store=new OpsStore(root);var jobs=new OpsJobs(store,(a,r,h,p)->Map.of())){
            var job = jobs.get("test");
            assertThat(job.path("state").asString()).isEqualTo("INTERRUPTED");
            assertThat(job.path("protectionBackupId").asString()).isEqualTo("safe-backup");
            assertThat(job.path("reconciledAt").asString()).isNotBlank();
            assertThat(jobs.recoveryStatus(store.read()).incident()).isNull();
        }
    }
    @Test void invalidTargetsPathsAndCommandsCannotBeSubmitted(){for(String action:List.of("SHELL","SQL","DELETE"))assertThatThrownBy(()->OpsJobs.validate(action,"business","")).isInstanceOf(OpsApplication.Failure.class);assertThatThrownBy(()->OpsJobs.validate("RESTORE","business","../outside")).isInstanceOf(OpsApplication.Failure.class);assertThatThrownBy(()->OpsJobs.validate("RESTART","mysql","")).isInstanceOf(OpsApplication.Failure.class);}
    @Test void httpAuthIsolationLogoutAndStatusWorkWithoutBusinessDatabase()throws Exception {
        Path control=root.resolve("control");Files.createDirectories(control);OpsStore.atomic(control.resolve("credentials.json"),OpsStore.JSON.writeValueAsBytes(Map.of("login","testops","passwordHash",new BCryptPasswordEncoder(4).encode("test-password"))));
        try (var journal = new OpsStore(control)) {
            journal.change(state -> state.withArray("jobs").addObject()
                    .put("id","interrupted-job").put("action","DEPLOY").put("state","RUNNING")
                    .put("protectionBackupId","handoff-backup"));
        }
        int port;try(var socket=new java.net.ServerSocket(0)){port=socket.getLocalPort();}
        var config=new OpsConfig(control,root.resolve("runtime"),root.resolve("artifacts"),root.resolve("backups"),root.resolve("media"),Path.of("unused"),Path.of("unused"),Path.of("unused"),root.resolve("missing-env"),port,"http://127.0.0.1:1/actuator/health","http://127.0.0.1:5173","campushub_test",null);
        try(var app=new OpsApplication(config)){app.start();var http=HttpClient.newHttpClient();String base="http://127.0.0.1:"+port+"/api/v1/ops";
            for(String token:List.of("adm_"+"x".repeat(43),"x".repeat(43))){var r=http.send(HttpRequest.newBuilder(URI.create(base+"/status")).header("Authorization","Bearer "+token).GET().build(),HttpResponse.BodyHandlers.ofString());assertThat(r.statusCode()).isEqualTo(401);assertThat(r.body()).doesNotContain(token);}
            var login=http.send(HttpRequest.newBuilder(URI.create(base+"/auth/login")).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString("{\"login\":\"testops\",\"password\":\"test-password\"}")).build(),HttpResponse.BodyHandlers.ofString());assertThat(login.statusCode()).isEqualTo(200);String token=OpsStore.JSON.readTree(login.body()).path("data").path("accessToken").asString();
            var request=HttpRequest.newBuilder(URI.create(base+"/status")).header("Authorization","Bearer "+token).GET().build();var status=http.send(request,HttpResponse.BodyHandlers.ofString());assertThat(status.statusCode()).isEqualTo(200);assertThat(status.body()).contains("\"healthy\":false").doesNotContain("password","missing-env");assertThat(status.headers().firstValue("Cache-Control")).contains("no-store");
            var backup = OpsStore.JSON.readTree(status.body()).path("data").path("dailyBackup");
            assertThat(backup.path("enabled").asBoolean()).isFalse();
            assertThat(backup.path("lastScheduledFor").isNull()).isTrue();
            var recovery = OpsStore.JSON.readTree(status.body()).path("data");
            assertThat(recovery.path("recoveryRequired").asBoolean()).isTrue();
            assertThat(recovery.path("recoveryIncident").path("protectionBackupId").asString()).isEqualTo("handoff-backup");
            assertThat(recovery.path("recoveryIncident").path("errorCode").asString()).isEqualTo("EXECUTOR_INTERRUPTED");
            var logUri = URI.create(base+"/logs?errorsOnly=true");
            assertThat(http.send(HttpRequest.newBuilder(logUri).GET().build(),HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(401);
            var logs = http.send(HttpRequest.newBuilder(logUri).header("Authorization","Bearer "+token).GET().build(),HttpResponse.BodyHandlers.ofString());
            assertThat(logs.statusCode()).isEqualTo(200);
            assertThat(OpsStore.JSON.readTree(logs.body()).path("data").path("businessRequests").isEmpty()).isTrue();
            assertThat(http.send(HttpRequest.newBuilder(URI.create(base+"/jobs")).header("Authorization","Bearer "+token).header("Origin","https://evil.example").GET().build(),HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(403);
            http.send(HttpRequest.newBuilder(URI.create(base+"/auth/logout")).header("Authorization","Bearer "+token).POST(HttpRequest.BodyPublishers.noBody()).build(),HttpResponse.BodyHandlers.ofString());assertThat(http.send(request,HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(401);
        }
    }
    @Test void durableLoginGateDoesNotResetAtExecutorRestart()throws Exception {
        Files.createDirectories(root);OpsStore.atomic(root.resolve("credentials.json"),OpsStore.JSON.writeValueAsBytes(Map.of("login","testops","passwordHash",new BCryptPasswordEncoder(4).encode("test-password"))));
        try(var store=new OpsStore(root)){var auth=new OpsAuth(store);for(int i=0;i<5;i++)assertThatThrownBy(()->auth.login("unknown","wrong")).hasMessage("OPS_LOGIN_FAILED");}
        try(var store=new OpsStore(root)){assertThatThrownBy(()->new OpsAuth(store).login("testops","test-password")).hasMessage("OPS_RATE_LIMITED");}
    }

    @Test void dailyBackupRunsAfterLocalTimeOnlyWhenReadyAndDoesNotRepeatAfterRestart() throws Exception {
        var clock = new MutableClock(); // 2030-01-01 08:00 in Asia/Shanghai
        var ready = new AtomicBoolean(false);
        var calls = new AtomicInteger();
        OpsJobs.Executor backup = (action, reference, hash, phase) -> {
            assertThat(action).isEqualTo("BACKUP");
            calls.incrementAndGet();
            return Map.of("backupId", "controlled");
        };
        var time = LocalTime.of(8, 1);
        try (var store = new OpsStore(root); var jobs = new OpsJobs(store, backup);
                var timer = new OpsDailyBackup(time, clock, jobs, ready::get)) {
            timer.tick();
            clock.advance(Duration.ofMinutes(1));
            timer.tick();
            assertThat(jobs.list().size()).isZero();
            ready.set(true);
            timer.tick();
            timer.tick();
            assertThat(jobs.list().size()).isEqualTo(1);
            assertThat(jobs.list().get(0).path("scheduledFor").asString()).isEqualTo("2030-01-01");
            assertThat(jobs.list().get(0).path("actorDomain").asString()).isEqualTo("OPS_SCHEDULER");
        }
        try (var store = new OpsStore(root); var jobs = new OpsJobs(store, backup);
                var timer = new OpsDailyBackup(time, clock, jobs, ready::get)) {
            timer.tick();
            assertThat(jobs.list().size()).isEqualTo(1);
            clock.advance(Duration.ofDays(2));
            timer.tick();
            assertThat(jobs.list().size()).isEqualTo(2); // No backfill for the missed date.
            assertThat(jobs.list().get(0).path("scheduledFor").asString()).isEqualTo("2030-01-03");
        }
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test void scheduledBackupNeverOverlapsManualWorkAndWaitsForRecoveryClearance() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var date = LocalDate.of(2030, 1, 1);
        String id;
        try (var store = new OpsStore(root);
                var jobs = new OpsJobs(store, (action, reference, hash, phase) -> {
                    phase.protectionReady("ready-backup");
                    entered.countDown();
                    release.await();
                    throw new OpsApplication.Failure(409, "OPS_MANUAL_RECOVERY_REQUIRED");
                })) {
            String confirmation = (String) jobs.prepare("DEPLOY","business","release-one","token",null).get("confirmationId");
            try {
                id = jobs.submit("DEPLOY","business","release-one",UUID.randomUUID().toString(),confirmation,"token").path("id").asString();
                assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();
                var checkpoint = OpsStore.JSON.readTree(Files.readAllBytes(root.resolve("state.json"))).path("jobs").get(0);
                assertThat(checkpoint.path("protectionBackupId").asString()).isEqualTo("ready-backup");
                assertThat(checkpoint.path("events").get(0).path("phase").asString()).isEqualTo("PROTECTION_READY");
                assertThat(jobs.scheduleBackup(date)).isFalse();
                assertThat(store.read().has("lastScheduledBackupDate")).isFalse();
            } finally {
                release.countDown();
            }
        }
        try (var store = new OpsStore(root); var jobs = new OpsJobs(store, (action, reference, hash, phase) -> Map.of())) {
            assertThat(jobs.scheduleBackup(date)).isFalse();
            assertThat(jobs.recoveryStatus(store.read()).incident().jobId()).isEqualTo(id);
            assertThat(jobs.recoveryStatus(store.read()).incident().protectionBackupId()).isEqualTo("ready-backup");
            store.reconcile();
            assertThat(jobs.scheduleBackup(date)).isTrue();
            assertThat(jobs.scheduleBackup(date)).isFalse();
        }
    }

    @Test void scheduleTimeIsOptionalAndOnlyAcceptsTwentyFourHourClock() throws Exception {
        var absent = OpsStore.JSON.createObjectNode().path("dailyBackupTime");
        assertThat(OpsConfig.parseDailyBackupTime(absent)).isNull();
        assertThat(OpsConfig.parseDailyBackupTime(OpsStore.JSON.readTree("\"03:05\""))).isEqualTo(LocalTime.of(3,5));
        for (String invalid : List.of("\"24:00\"", "\"3:05\"", "\"\"", "1200")) {
            assertThatThrownBy(() -> OpsConfig.parseDailyBackupTime(OpsStore.JSON.readTree(invalid)))
                    .hasMessage("OPS_DAILY_BACKUP_TIME_INVALID");
        }
    }
}
