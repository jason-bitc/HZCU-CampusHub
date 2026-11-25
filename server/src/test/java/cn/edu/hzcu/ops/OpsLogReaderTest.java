package cn.edu.hzcu.ops;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpsLogReaderTest {
    private static final String EARLIER = "00000000-0000-4000-8000-000000000001";
    private static final String LATER = "00000000-0000-4000-8000-000000000002";
    private static final String PARTIAL = "00000000-0000-4000-8000-000000000003";
    @TempDir Path directory;

    @Test void combinesRotationsWithoutReturningRawContextOrPartialLines() throws Exception {
        String earlier = request(EARLIER, "00", 503) + " module=READING code=SERVICE_UNAVAILABLE actorId=42 token=private";
        String later = request(LATER, "01", 200);
        Files.writeString(directory.resolve("campushub-runtime.1.log"), earlier + "\n" + later + "\n");
        Files.writeString(directory.resolve("campushub-runtime.log"), later + "\n"
                + "extra event=REQUEST action=ARTICLE_DETAIL code=PRIVATE\n" + request(PARTIAL, "02", 404));
        Files.writeString(directory.resolve("campushub-diagnostic.log"), earlier.replace(EARLIER,PARTIAL) + "\n");

        var all = OpsLogReader.read(directory, OpsLogReader.parseQuery(null));
        assertThat(all.requests()).extracting(entry -> entry.get("traceId")).containsExactly(EARLIER,LATER);
        assertThat(all.requests().getFirst()).containsEntry("occurredAt", "2030-01-01T00:00:00Z")
                .containsEntry("code", "SERVICE_UNAVAILABLE").doesNotContainKeys("actorId", "token");
        assertThat(all.coverage()).isEqualTo(new OpsLogReader.Coverage(2,false,false));

        var errors = OpsLogReader.read(directory, OpsLogReader.parseQuery("errorsOnly=true&limit=1&traceId="+EARLIER));
        assertThat(errors.requests()).extracting(entry -> entry.get("status")).containsExactly("503");
        var latest = OpsLogReader.read(directory, OpsLogReader.parseQuery("limit=1"));
        assertThat(latest.requests()).extracting(entry -> entry.get("traceId")).containsExactly(LATER);
    }

    @Test void boundsBytesAndReportsUnavailableFiles() throws Exception {
        Files.writeString(directory.resolve("campushub-runtime.1.log"), request(EARLIER,"00",404) + "\n"
                + "x".repeat(256 * 1024) + "\n" + request(LATER,"01",200) + "\n");
        Files.createDirectory(directory.resolve("campushub-runtime.log"));

        var result = OpsLogReader.read(directory, OpsLogReader.parseQuery(null));
        assertThat(result.requests()).extracting(entry -> entry.get("traceId")).containsExactly(LATER);
        assertThat(result.coverage()).isEqualTo(new OpsLogReader.Coverage(1,true,true));
    }

    @Test void rejectsDuplicatesAndUnsupportedFilters() {
        for (String invalid : List.of("", "traceId=NONE", "traceId=../test", "errorsOnly=yes", "limit=0", "limit=101",
                "limit=1&limit=2", "file=runtime.log", "limit=1&")) {
            assertThatThrownBy(() -> OpsLogReader.parseQuery(invalid)).hasMessage("INVALID_PARAMETER");
        }
    }

    private static String request(String trace, String seconds, int status) {
        String result = Map.of(200,"OK",404,"DENIED",503,"FAILED").get(status);
        return "2030-01-01T00:00:" + seconds + "Z traceId=" + trace
                + " event=REQUEST action=ARTICLE_DETAIL method=GET status=" + status
                + " result=" + result + " durationMs=4";
    }
}
