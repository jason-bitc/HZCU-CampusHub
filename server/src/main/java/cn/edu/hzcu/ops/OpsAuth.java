package cn.edu.hzcu.ops;

import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.security.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

final class OpsAuth {
    private final OpsStore store;
    private final Map<String,Instant> sessions=new HashMap<>();
    private final BCryptPasswordEncoder passwords=new BCryptPasswordEncoder(12);
    OpsAuth(OpsStore store){this.store=store;}
    static String digest(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    synchronized Map<String,Object> login(String login,String password)throws IOException {
        var credentials=OpsStore.JSON.readTree(Files.readString(store.root.resolve("credentials.json")));
        var state=store.read();Instant now=Instant.now(),start=Instant.parse(state.path("loginWindow").asString(Instant.EPOCH.toString()));
        int failed=now.isBefore(start.plusSeconds(900))?state.path("loginFailures").asInt():0;
        if(failed>=5)throw new OpsApplication.Failure(429,"OPS_RATE_LIMITED");
        boolean match=password.length()<=64&&password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length<=72&&passwords.matches(password,credentials.path("passwordHash").asString());
        if(!MessageDigest.isEqual(login.getBytes(java.nio.charset.StandardCharsets.UTF_8),credentials.path("login").asString().getBytes(java.nio.charset.StandardCharsets.UTF_8))||!match){
            store.change(n->n.put("loginFailures",failed+1).put("loginWindow",failed==0?now.toString():start.toString()));throw new OpsApplication.Failure(401,"OPS_LOGIN_FAILED");
        }
        store.change(n->n.put("loginFailures",0).put("loginWindow",now.toString()));
        sessions.entrySet().removeIf(e->!now.isBefore(e.getValue()));if(sessions.size()>=100)throw new OpsApplication.Failure(429,"OPS_SESSION_LIMIT");
        byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);String token="ops_"+Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);Instant expiry=now.plusSeconds(28800);sessions.put(digest(token),expiry);
        return Map.of("tokenType","Bearer","accessToken",token,"expiresAt",expiry.toString());
    }
    synchronized void authenticate(String token){if(token==null||!token.matches("ops_[A-Za-z0-9_-]{43}")||!Instant.now().isBefore(sessions.getOrDefault(digest(token),Instant.EPOCH)))throw new OpsApplication.Failure(401,"OPS_UNAUTHORIZED");}
    synchronized void logout(String token){sessions.remove(digest(token));}
    static void initialize(Path root)throws IOException {
        Files.createDirectories(root);OpsStore.rejectLinks(root);
        Console console=System.console();var input=new BufferedReader(new InputStreamReader(System.in,java.nio.charset.StandardCharsets.UTF_8));
        String login=console==null?input.readLine():console.readLine("Ops login: ");
        char[] a=console==null?Objects.requireNonNull(input.readLine()).toCharArray():console.readPassword("New password: "),b=console==null?Objects.requireNonNull(input.readLine()).toCharArray():console.readPassword("Repeat password: ");
        try{String password=new String(a);if(!login.matches("[a-z0-9][a-z0-9._-]{2,63}")||!Arrays.equals(a,b)||password.length()<8||password.length()>64||password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72)throw new IOException("OPS_SETUP_INVALID");
            try(var store=new OpsStore(root)){OpsStore.atomic(root.resolve("credentials.json"),OpsStore.JSON.writeValueAsBytes(Map.of("login",login,"passwordHash",new BCryptPasswordEncoder(12).encode(password))));store.change(n->n.put("loginFailures",0));}
        }finally{Arrays.fill(a,'\0');Arrays.fill(b,'\0');}
    }
}
