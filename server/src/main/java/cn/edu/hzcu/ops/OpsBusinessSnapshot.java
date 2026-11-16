package cn.edu.hzcu.ops;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.DigestOutputStream;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;

/** Versioned checks of selected business columns, in primary-key order. */
final class OpsBusinessSnapshot {
    private static final int VERSION = 1;
    private static final List<Table> TABLES = List.of(
            new Table("article", "id", "id,state,public_version_id,effective_coins,effective_likes"),
            new Table("article_version", "id", "id,article_id,version_number,title,summary,body,experience_stage,source_type"),
            new Table("article_draft", "article_id", "article_id,revision,title,summary,body,experience_stage,source_type"),
            new Table("category", "id", "id,kind,name,enabled,display_order,canonical_id"),
            new Table("discussion_comment", "id", "id,article_id,user_id,root_comment_id,reply_to_comment_id,state,revision,body"),
            new Table("coin_wallet", "user_id", "user_id,balance,revision"),
            new Table("coin_event", "sequence_id", "sequence_id,event_id,user_id,action,article_id,delta,balance_after,wallet_revision,effective"),
            new Table("community_case", "id", "id,user_id,kind,category,article_id,comment_id,state,revision,assignee_id,content_digest"));

    private record Table(String name, String primaryKey, String columns) {}
    record Digest(long rows, String sha256) {}
    record Snapshot(int version, Map<String, Digest> tables) {}
    record Verification(String status, List<String> checkedTables) {}

    private OpsBusinessSnapshot() {}

    static Snapshot capture(Connection connection) throws SQLException, IOException {
        var digests = new LinkedHashMap<String, Digest>();
        for (Table table : TABLES) digests.put(table.name(), digest(connection, table));
        return new Snapshot(VERSION, digests);
    }

    static Snapshot read(JsonNode value) {
        if (value.isMissingNode()) return null;
        var version = value.path("version");
        var tables = value.path("tables");
        if (!value.isObject() || value.size() != 2 || !version.isIntegralNumber() || !version.canConvertToInt() || version.asInt() != VERSION
                || !tables.isObject() || tables.size() != TABLES.size()) {
            throw OpsRunner.failure("OPS_BACKUP_INVALID");
        }
        var digests = new LinkedHashMap<String, Digest>();
        for (Table table : TABLES) {
            var entry = tables.path(table.name());
            var rows = entry.path("rows");
            var hash = entry.path("sha256");
            if (!entry.isObject() || entry.size() != 2 || !rows.isIntegralNumber() || !rows.canConvertToLong()
                    || rows.asLong() < 0 || !hash.isString() || !hash.asString().matches("[0-9a-f]{64}")) {
                throw OpsRunner.failure("OPS_BACKUP_INVALID");
            }
            digests.put(table.name(), new Digest(rows.asLong(), hash.asString()));
        }
        return new Snapshot(VERSION, digests);
    }

    static Verification verify(Snapshot expected, Snapshot actual) {
        if (expected == null) return new Verification("UNAVAILABLE", List.of());
        if (!expected.equals(actual)) throw OpsRunner.failure("OPS_BUSINESS_SNAPSHOT_MISMATCH");
        return new Verification("VERIFIED", TABLES.stream().map(Table::name).toList());
    }

    private static Digest digest(Connection connection, Table table) throws SQLException, IOException {
        var digest = OpsRunner.sha256();
        long count = 0;
        try (var statement = connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
             var bytes = new DataOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(), digest))) {
            // Connector/J streams results for this forward-only fetch size.
            statement.setFetchSize(Integer.MIN_VALUE);
            try (var rows = statement.executeQuery("SELECT " + table.columns() + " FROM " + table.name()
                    + " ORDER BY " + table.primaryKey())) {
                int columns = rows.getMetaData().getColumnCount();
                while (rows.next()) {
                    for (int column = 1; column <= columns; column++) {
                        byte[] value = rows.getBytes(column);
                        bytes.writeInt(value == null ? -1 : value.length);
                        if (value != null) bytes.write(value);
                    }
                    count++;
                }
            }
        }
        return new Digest(count, HexFormat.of().formatHex(digest.digest()));
    }
}
