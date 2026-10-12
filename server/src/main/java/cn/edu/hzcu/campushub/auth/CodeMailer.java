package cn.edu.hzcu.campushub.auth;

public interface CodeMailer {
    /** Implementations must never log mailbox contents, codes or authentication material. */
    void send(String email,String purpose,String code);
}
