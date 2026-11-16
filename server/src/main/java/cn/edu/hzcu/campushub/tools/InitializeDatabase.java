package cn.edu.hzcu.campushub.tools;

import java.sql.DriverManager;

/** Explicit local development setup utility. Not invoked by application startup. */
public final class InitializeDatabase {
    public static void main(String[] args) {
        String host = System.getenv().getOrDefault("CAMPUSHUB_DB_HOST", "127.0.0.1");
        String port = System.getenv().getOrDefault("CAMPUSHUB_DB_PORT", "3306");
        if (!host.equals("127.0.0.1") && !host.equals("localhost")) {
            System.err.println("Setup supports local MySQL only."); System.exit(1);
        }
        try {
            if (!port.matches("[0-9]{1,5}")) throw new IllegalArgumentException();
            String username = System.getenv("CAMPUSHUB_DB_USERNAME");
            String password = System.getenv("CAMPUSHUB_DB_PASSWORD");
            if (username == null || password == null || password.isBlank()) throw new IllegalArgumentException();
            try (var connection = DriverManager.getConnection("jdbc:mysql://" + host + ":" + port
                    + "/?connectTimeout=3000&socketTimeout=5000", username, password);
                    var statement = connection.createStatement()) {
                // Literal targets only. Never drop/reset databases, tables or users.
                statement.executeUpdate("CREATE DATABASE IF NOT EXISTS campushub_dev CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
                statement.executeUpdate("CREATE DATABASE IF NOT EXISTS campushub_test CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
                System.out.println("Project databases ready: campushub_dev, campushub_test. Existing data preserved.");
            }
        } catch (Exception e) {
            if (e instanceof java.sql.SQLException sql) {
                System.err.println("MySQL SQLState=" + sql.getSQLState() + " vendorCode=" + sql.getErrorCode());
                if (String.valueOf(sql.getMessage()).contains("Public Key Retrieval is not allowed"))
                    System.err.println("MySQL requires a trusted RSA key or TLS for authentication. Local-only setup may enable explicit public key retrieval.");
            }
            System.err.println("Project database setup failed [" + e.getClass().getSimpleName() + "]. Check authorized local credentials and CREATE privilege.");
            System.exit(1);
        }
    }
}
