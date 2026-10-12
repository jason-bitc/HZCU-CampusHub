package cn.edu.hzcu.campushub.auth;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AuthCrypto {
    private final byte[] pepper;
    private final SecureRandom random = new SecureRandom();
    public AuthCrypto(@Value("${campushub.auth.pepper}") String key) {
        try { pepper = Base64.getDecoder().decode(key); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException("Invalid local authentication key"); }
        if (pepper.length < 32) throw new IllegalArgumentException("Set a persistent authentication key of at least 256 bits");
    }
    public String token() { byte[] bytes = new byte[32]; random.nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    public String code() { return String.format(Locale.ROOT,"%06d",random.nextInt(1_000_000)); }
    public String codeDigest(String id,String email,String purpose,String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(pepper,"HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal((id+"\n"+email+"\n"+purpose+"\n"+code).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) { throw new IllegalStateException("Authentication digest unavailable"); }
    }
    public static String tokenDigest(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (GeneralSecurityException e) { throw new IllegalStateException("Session digest unavailable"); }
    }
    public static boolean equal(String left,String right) {
        return MessageDigest.isEqual(left.getBytes(StandardCharsets.US_ASCII),right.getBytes(StandardCharsets.US_ASCII));
    }
}
