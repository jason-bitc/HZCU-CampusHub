package cn.edu.hzcu.campushub.auth;

import java.time.Instant;

public final class AuthModels {
    private AuthModels() {}
    public enum Purpose { REGISTER, PASSWORD_RESET }
    public record CodeRequest(String challengeId, Instant expiresAt, Instant resendAt, String notice) {}
    public record Me(long userId,String email,String role,String accountState,String campusCertification,Instant verifiedAt) {}
    public record Login(String tokenType,String accessToken,Instant expiresAt) {
        @Override public String toString(){return "Login[redacted]";}
    }
    record User(long id,String email,String hash,String state,String role,String certState,Instant verifiedAt) {
        Me me(){return new Me(id,email,role,state,certState,verifiedAt);}
        boolean allowed(){return "ACTIVE".equals(state)&&"VALID".equals(certState)&&"USER".equals(role);}
        @Override public String toString(){return "User[redacted]";}
    }
    record Challenge(String id,String email,String purpose,String digest,String status,int attempts,Instant expiresAt,Long resetUserId) {
        @Override public String toString(){return "Challenge[redacted]";}
    }
    public record Identity(long sessionId,Me me) {}
}
