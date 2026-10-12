package cn.edu.hzcu.campushub.common;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(DatabaseConfig.Settings.class)
public class DatabaseConfig {
    @ConfigurationProperties("campushub.db")
    public static final class Settings {
        private String host, name, username, password;
        private int port;
        public String getHost() { return host; }
        public void setHost(String value) { host = value; }
        public String getName() { return name; }
        public void setName(String value) { name = value; }
        public String getUsername() { return username; }
        public void setUsername(String value) { username = value; }
        public String getPassword() { return password; }
        public void setPassword(String value) { password = value; }
        public int getPort() { return port; }
        public void setPort(int value) { port = value; }
        public void validate() {
            if (host == null || !host.matches("[a-zA-Z0-9.-]{1,253}") || port < 1 || port > 65535)
                throw new IllegalArgumentException("Invalid CAMPUSHUB_DB_HOST or CAMPUSHUB_DB_PORT");
            if (!"campushub_dev".equals(name) && !"campushub_test".equals(name))
                throw new IllegalArgumentException("Database must be campushub_dev or campushub_test");
            if (username == null || username.isBlank() || password == null || password.isBlank())
                throw new IllegalArgumentException("CAMPUSHUB_DB_USERNAME and CAMPUSHUB_DB_PASSWORD are required");
        }
    }

    @Bean(destroyMethod = "close")
    HikariDataSource dataSource(Settings settings) {
        settings.validate();
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://" + settings.getHost() + ":" + settings.getPort() + "/" + settings.getName()
                + "?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&characterEncoding=UTF-8"
                + "&connectTimeout=3000&socketTimeout=5000");
        config.setUsername(settings.getUsername());
        config.setPassword(settings.getPassword());
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(0);
        config.setConnectionTimeout(4000);
        config.setValidationTimeout(2000);
        config.setPoolName("campushub");
        return new HikariDataSource(config);
    }
}
