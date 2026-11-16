package cn.edu.hzcu.campushub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CampusHubApplication {
    public static void main(String[] args) {
        try {
            SpringApplication.run(CampusHubApplication.class, args);
        } catch (Exception failure) {
            // Do not print configuration values or vendor exception messages (may contain secrets).
            Throwable cause = failure;
            String category = "STARTUP_FAILED";
            while (cause != null) {
                if (cause.getClass().getName().startsWith("org.flywaydb.")) category = "MIGRATION_FAILED";
                if (cause instanceof IllegalArgumentException) category = "CONFIG_INVALID";
                if (cause instanceof cn.edu.hzcu.campushub.audit.SafeFileLogs.StorageUnavailable) category = "LOG_STORAGE_UNAVAILABLE";
                if (cause instanceof java.sql.SQLException sql) {
                    if ("28000".equals(sql.getSQLState())) category = "DB_AUTH_FAILED";
                    else if (!"MIGRATION_FAILED".equals(category) || String.valueOf(sql.getSQLState()).startsWith("08"))
                        category = "DB_CONNECTION_FAILED";
                }
                if (cause.getCause() == cause) break;
                cause = cause.getCause();
            }
            System.err.println("CampusHub startup failed [" + category
                    + "]. Check local configuration, database/storage permissions and Flyway migrations."
                    + " No application is ready; see server/README.md troubleshooting.");
            System.exit(1);
        }
    }
}
