package cn.edu.hzcu.campushub.common;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DatabaseConfigTest {
    @Test void otherDatabasesAndUrlInjectionRejectedWithoutEchoingSecrets() {
        var s = new DatabaseConfig.Settings(); s.setHost("127.0.0.1"); s.setPort(3306);
        s.setUsername("test"); s.setPassword("sensitive-test-sentinel"); s.setName("other_project");
        assertThatThrownBy(s::validate).hasMessage("Database must be campushub_dev or campushub_test")
                .hasMessageNotContaining("sensitive-test-sentinel");
        s.setName("campushub_dev"); s.setHost("localhost/?password=value");
        assertThatThrownBy(s::validate).hasMessage("Invalid CAMPUSHUB_DB_HOST or CAMPUSHUB_DB_PORT");
    }
    @Test void missingCredentialsFailWithoutRejectedValues() {
        var s = new DatabaseConfig.Settings(); s.setHost("127.0.0.1"); s.setPort(3306); s.setName("campushub_dev");
        assertThatThrownBy(s::validate).hasMessage("CAMPUSHUB_DB_USERNAME and CAMPUSHUB_DB_PASSWORD are required");
    }
}
