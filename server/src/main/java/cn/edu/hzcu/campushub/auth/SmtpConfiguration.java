package cn.edu.hzcu.campushub.auth;

import java.util.Properties;
import org.springframework.boot.context.properties.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.*;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;

@Configuration
@EnableConfigurationProperties(SmtpConfiguration.Settings.class)
public class SmtpConfiguration {
    @ConfigurationProperties("campushub.mail")
    public static class Settings {
        private String host,from,username,password,tlsMode;
        private int port,timeoutMs;
        public String getHost(){return host;} public void setHost(String v){host=v;}
        public String getFrom(){return from;} public void setFrom(String v){from=v;}
        public String getUsername(){return username;} public void setUsername(String v){username=v;}
        public String getPassword(){return password;} public void setPassword(String v){password=v;}
        public String getTlsMode(){return tlsMode;} public void setTlsMode(String v){tlsMode=v;}
        public int getPort(){return port;} public void setPort(int v){port=v;}
        public int getTimeoutMs(){return timeoutMs;} public void setTimeoutMs(int v){timeoutMs=v;}
        public void validate() {
            if (host==null || !host.matches("[a-zA-Z0-9.-]{1,253}") || port<1 || port>65535
                    || timeoutMs<1000 || timeoutMs>30000 || !("SSL".equals(tlsMode)||"STARTTLS".equals(tlsMode)))
                throw new IllegalArgumentException("Invalid SMTP host, port, mandatory TLS mode or timeout");
            if (from==null || !from.matches("[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.-]+") || !from.equals(username)
                    || password==null || password.isBlank()) throw new IllegalArgumentException("SMTP sender and local client authorization secret required");
        }
    }
    @Bean @ConditionalOnMissingBean(CodeMailer.class)
    CodeMailer smtpMailer(Settings settings) {
        settings.validate();
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(settings.getHost()); sender.setPort(settings.getPort());
        sender.setUsername(settings.getUsername()); sender.setPassword(settings.getPassword()); sender.setDefaultEncoding("UTF-8");
        Properties p=sender.getJavaMailProperties();
        p.setProperty("mail.smtp.auth","true"); p.setProperty("mail.debug","false");
        p.setProperty("mail.smtp.ssl.enable",String.valueOf("SSL".equals(settings.getTlsMode())));
        p.setProperty("mail.smtp.starttls.enable",String.valueOf("STARTTLS".equals(settings.getTlsMode())));
        p.setProperty("mail.smtp.starttls.required",String.valueOf("STARTTLS".equals(settings.getTlsMode())));
        p.setProperty("mail.smtp.ssl.checkserveridentity","true");
        p.setProperty("mail.smtp.ssl.protocols","TLSv1.2 TLSv1.3");
        for(String timeout: new String[]{"connectiontimeout","timeout","writetimeout"}) p.setProperty("mail.smtp."+timeout,String.valueOf(settings.getTimeoutMs()));
        return (email,purpose,code)-> {
            SimpleMailMessage message=new SimpleMailMessage(); message.setFrom(settings.getFrom()); message.setTo(email);
            message.setSubject("城院帮"+(purpose.equals("REGISTER")?"注册":"密码重置")+"验证码");
            message.setText("你的城院帮"+(purpose.equals("REGISTER")?"注册":"密码重置")+"验证码为："+code+"\n有效期5分钟，仅供本人使用。\n校园邮箱认证不代表工作经历认证，也不会创建管理员权限。\n如果不是你发起，请忽略本邮件。");
            try { sender.send(message); }
            catch (MailException e) {
                // Vendor messages may contain addresses or auth material. Preserve only safe classification.
                throw new MailDeliveryFailure(classify(e));
            }
        };
    }
    static String classify(Throwable failure) {
        for (Throwable current=failure; current!=null; current=current.getCause()) {
            if(current instanceof java.net.UnknownHostException)return "SMTP_DNS_FAILED";
            if(current instanceof javax.net.ssl.SSLException)return "SMTP_TLS_FAILED";
            if(current instanceof java.net.SocketTimeoutException)return "SMTP_TIMEOUT";
        }
        if(failure instanceof org.springframework.mail.MailAuthenticationException)return "SMTP_AUTH_FAILED";
        return "SMTP_DELIVERY_FAILED";
    }
    public static class MailDeliveryFailure extends RuntimeException {
        public MailDeliveryFailure(String category){super(category);}
    }
}
