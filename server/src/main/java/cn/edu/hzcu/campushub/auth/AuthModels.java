package cn.edu.hzcu.campushub.auth;

import java.time.Instant;

public final class AuthModels {
    private AuthModels() {}
    public enum Purpose { REGISTER, PASSWORD_RESET, CAMPUS_REVERIFY }
    public record CodeRequest(String challengeId, Instant expiresAt, Instant resendAt, String notice) {}
    public record Me(long userId,String email,String role,String accountState,String campusCertification,Instant verifiedAt) {}
    public record CertificationStatus(String state, Instant verifiedAt, boolean canReverify) {}
    public record Login(String tokenType,String accessToken,Instant expiresAt) {
        @Override public String toString(){return "Login[redacted]";}
    }
    record User(long id,String email,String hash,String state,String role,String certState,Instant verifiedAt,long certRevision) {
        Me me(){return new Me(id,email,role,state,certState,verifiedAt);}
        boolean canSignIn(){return "ACTIVE".equals(state)&&"USER".equals(role);}
        @Override public String toString(){return "User[redacted]";}
    }
    record Recovery(long session, long revision) {}
    record Challenge(String id,String email,String purpose,String digest,String status,int attempts,Instant expiresAt,
                     Long resetUserId, Long reverifySessionId, Long reverifyRevision) {
        @Override public String toString(){return "Challenge[redacted]";}
    }
    public record Identity(long sessionId,Me me) {}
}
