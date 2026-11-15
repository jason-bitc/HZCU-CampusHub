package cn.edu.hzcu.ops;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.regex.Pattern;

final class OpsMediaIntegrity {
    private static final Pattern OBJECT_ID = Pattern.compile("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}");
    private static final String REFERENCES = """
            SELECT id,size_bytes,content_digest FROM media_image
            UNION ALL SELECT id,size_bytes,content_digest FROM comment_image
            UNION ALL SELECT id,size_bytes,content_digest FROM community_case_image
            UNION ALL SELECT image_id,size_bytes,content_digest FROM community_case_media
            """;

    private record Metadata(long bytes,String sha256) {}

    private OpsMediaIntegrity() {}

    static long verify(Connection connection,Path mediaRoot) throws IOException,SQLException {
        Path objects=mediaRoot.resolve("objects");
        var checked=new HashMap<String,Metadata>();
        long references=0;
        try (var statement=connection.createStatement();var rows=statement.executeQuery(REFERENCES)) {
            while (rows.next()) {
                String id=rows.getString(1);
                var expected=new Metadata(rows.getLong(2),rows.getString(3));
                var prior=checked.get(id);
                if (prior==null) {
                    verifyObject(objects,id,expected);
                    checked.put(id,expected);
                } else if (!prior.equals(expected)) {
                    throw OpsRunner.failure("OPS_MEDIA_REFERENCE_MISMATCH");
                }
                references++;
            }
        }
        return references;
    }

    private static void verifyObject(Path objects,String id,Metadata expected) throws IOException {
        if (id==null||!OBJECT_ID.matcher(id).matches()||expected.bytes()<=0) {
            throw OpsRunner.failure("OPS_MEDIA_REFERENCE_MISMATCH");
        }
        Path file=objects.resolve(id);
        OpsStore.rejectLinks(file);
        if (!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||Files.size(file)!=expected.bytes()
                ||!OpsRunner.sha(file).equals(expected.sha256())) {
            throw OpsRunner.failure("OPS_MEDIA_REFERENCE_MISMATCH");
        }
    }
}
