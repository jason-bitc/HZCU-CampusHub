package cn.edu.hzcu.campushub.auth;

import cn.edu.hzcu.campushub.common.ApiException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import tools.jackson.databind.JsonNode;

public final class AuthInput {
    private AuthInput() {}
    public static String email(String value) {
        if (value == null) throw ApiException.invalid();
        String email = value.strip().toLowerCase(Locale.ROOT);
        if (!email.matches("[a-z0-9](?:[a-z0-9._+%\\-]{0,62}[a-z0-9])?@stu\\.hzcu\\.edu\\.cn")
                || email.contains("..")) throw ApiException.invalid();
        return email;
    }
    public static String password(String value) {
        if (value == null) throw ApiException.invalid();
        int characters = value.codePointCount(0,value.length());
        if (characters < 8 || characters > 64 || !StandardCharsets.UTF_8.newEncoder().canEncode(value)
                || value.getBytes(StandardCharsets.UTF_8).length > 72) throw ApiException.invalid();
        return value;
    }
    public static String challenge(String value) {
        if (value == null || !value.matches("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}")) throw ApiException.invalid();
        return value;
    }
    public static String code(String value) {
        if (value == null || !value.matches("[0-9]{6}")) throw ApiException.invalid();
        return value;
    }
    public static void fields(JsonNode body, String... fields) {
        if (body == null || !body.isObject() || body.size() != fields.length) throw ApiException.invalid();
        Set<String> expected = Set.of(fields);
        for (var field : body.properties()) if (!expected.contains(field.getKey()) || !field.getValue().isString()) throw ApiException.invalid();
    }
    public static String value(JsonNode body,String field) { return body.path(field).asString(); }
}
