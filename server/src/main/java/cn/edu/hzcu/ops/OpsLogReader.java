package cn.edu.hzcu.ops;

import cn.edu.hzcu.campushub.audit.SafeFileLogs;
import cn.edu.hzcu.campushub.common.RequestTrace;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

final class OpsLogReader {
    private static final String TRACE_ID = "[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}";
    private static final int MAX_ARCHIVES = 30;
    private static final int TAIL_BYTES = 256 * 1024;
    private static final Pattern REQUEST = Pattern.compile(
            "(?<time>[0-9TZ:.-]{20,35}) traceId=(?<traceId>" + TRACE_ID + "|NONE) event=REQUEST"
            + " action=(?<action>[A-Z][A-Z0-9_]{0,63}) method=(?<method>GET|POST|PUT|DELETE|PATCH|HEAD|OPTIONS|OTHER)"
            + " status=(?<status>[1-5][0-9]{2}) result=(?<result>OK|DENIED|CONFLICT|FAILED)"
            + " durationMs=(?<durationMs>[0-9]{1,19})"
            + "(?: module=(?<module>[A-Z][A-Z_]{0,31}) code=(?<code>[A-Z][A-Z0-9_]{1,47})(?: .*)?)?");

    record Query(String traceId, boolean errorsOnly, int limit) {}
    record Coverage(int filesRead, boolean scanLimited, boolean readIncomplete) {}
    record Result(List<Map<String,String>> requests, Coverage coverage) {}
    private record Tail(String text, boolean limited) {}

    private OpsLogReader() {}

    static Query parseQuery(String raw) {
        var parameters = new LinkedHashMap<String,String>();
        if (raw != null) {
            for (String pair : raw.split("&", -1)) {
                String[] parts = pair.split("=", 2);
                if (parts.length != 2 || parameters.putIfAbsent(parts[0], parts[1]) != null) throw invalidQuery();
            }
        }
        String traceId = parameters.remove("traceId");
        String errorsOnly = parameters.getOrDefault("errorsOnly","false");
        String limit = parameters.getOrDefault("limit","100");
        parameters.remove("errorsOnly");
        parameters.remove("limit");
        if (!parameters.isEmpty() || (traceId != null && !traceId.matches(TRACE_ID))
                || !(errorsOnly.equals("true") || errorsOnly.equals("false"))
                || !limit.matches("[1-9][0-9]?|100")) throw invalidQuery();
        return new Query(traceId, Boolean.parseBoolean(errorsOnly), Integer.parseInt(limit));
    }

    private static OpsApplication.Failure invalidQuery() {
        return new OpsApplication.Failure(400, "INVALID_PARAMETER");
    }

    static Result read(Path directory, Query query) {
        var newest = new ArrayList<Map<String,String>>();
        var seen = new HashSet<String>();
        boolean limited = false;
        boolean incomplete = false;
        int filesRead = 0;
        for (int index = 0; index <= MAX_ARCHIVES && newest.size() < query.limit(); index++) {
            Path file = directory.resolve(index == 0 ? "campushub-runtime.log" : "campushub-runtime." + index + ".log");
            if (Files.notExists(file, LinkOption.NOFOLLOW_LINKS)) continue;
            try {
                Tail tail = readTail(file);
                filesRead++;
                limited |= tail.limited();
                String[] lines = tail.text().split("\n");
                for (int row = lines.length - 1; row >= 0 && newest.size() < query.limit(); row--) {
                    var entry = parseRequest(lines[row]);
                    if (entry == null) continue;
                    String trace = entry.get("traceId");
                    if (query.traceId() != null && !query.traceId().equals(trace)) continue;
                    if (query.errorsOnly() && Integer.parseInt(entry.get("status")) < 400) continue;
                    if (trace.equals("NONE") || seen.add(trace)) newest.add(entry);
                }
            } catch (IOException unavailable) {
                // A rotating or unavailable business log must not break access to the task journal.
                incomplete = true;
            }
        }
        Collections.reverse(newest);
        return new Result(newest, new Coverage(filesRead, limited, incomplete));
    }

    private static Tail readTail(Path file) throws IOException {
        OpsStore.rejectLinks(file);
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || !file.equals(file.toRealPath())) {
            throw new IOException("OPS_LOG_FILE_UNAVAILABLE");
        }
        try (var channel = Files.newByteChannel(file, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            long size = channel.size();
            boolean limited = size > TAIL_BYTES;
            int length = (int) Math.min(size, TAIL_BYTES);
            ByteBuffer buffer = ByteBuffer.allocate(length);
            channel.position(size - length);
            while (buffer.hasRemaining() && channel.read(buffer) > 0) {
                // Read only the bytes present when the file was opened.
            }
            if (buffer.hasRemaining()) throw new IOException("OPS_LOG_FILE_CHANGED");
            String text = new String(buffer.array(), StandardCharsets.UTF_8);
            int firstLine = limited ? text.indexOf('\n') + 1 : 0;
            int lastNewline = text.lastIndexOf('\n');
            return new Tail(lastNewline < firstLine ? "" : text.substring(firstLine, lastNewline), limited);
        }
    }

    private static Map<String,String> parseRequest(String line) {
        if (line.length() > 2048) return null;
        var match = REQUEST.matcher(line.stripTrailing());
        if (!match.matches()) return null;
        String action = match.group("action");
        String module = match.group("module");
        if (!SafeFileLogs.ACTIONS.contains(action) || (module != null && !RequestTrace.module(action).equals(module))) return null;
        var entry = new LinkedHashMap<String,String>();
        try {
            entry.put("occurredAt", Instant.parse(match.group("time")).toString());
        } catch (DateTimeException invalid) {
            return null;
        }
        for (String field : List.of("traceId","action","method","status","result","durationMs","module","code")) {
            String value = match.group(field);
            if (value != null) entry.put(field,value);
        }
        return entry;
    }
}
