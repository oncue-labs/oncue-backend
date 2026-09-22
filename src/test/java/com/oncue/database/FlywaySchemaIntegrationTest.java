package com.oncue.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class FlywaySchemaIntegrationTest {

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
            .withDatabaseName("oncue")
            .withUsername("oncue")
            .withPassword("oncue");

    private static Flyway flyway;

    @BeforeAll
    static void configureFlyway() {
        flyway = Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .load();
    }

    @BeforeEach
    void resetSchema() {
        flyway.clean();
        flyway.migrate();
    }

    @Test
    void createsContractedTablesColumnsAndRelationshipsWithoutCombinationTables() throws SQLException {
        assertThat(tableNames()).containsExactlyInAnyOrder(
                "users",
                "user_login_accounts",
                "personas",
                "scenarios",
                "reservations",
                "call_sessions",
                "push_devices",
                "refresh_tokens");

        assertThat(columnNames("scenarios")).doesNotContain("persona_id");
        assertThat(columnNames("call_sessions")).doesNotContain("call_end_reason");
        assertThat(foreignKeyReferences()).containsExactlyInAnyOrder(
                "user_login_accounts.user_id->users.id",
                "reservations.user_id->users.id",
                "reservations.persona_id->personas.id",
                "reservations.scenario_id->scenarios.id",
                "call_sessions.reservation_id->reservations.id",
                "push_devices.user_id->users.id",
                "refresh_tokens.user_id->users.id");

        assertColumn("users", "id", "bigint unsigned", true);
        assertColumn("user_login_accounts", "id", "bigint unsigned", true);
        assertColumn("personas", "id", "bigint unsigned", true);
        assertColumn("scenarios", "id", "bigint unsigned", true);
        assertColumn("reservations", "id", "bigint unsigned", true);
        assertColumn("call_sessions", "id", "bigint unsigned", true);
        assertColumn("push_devices", "id", "bigint unsigned", true);
        assertColumn("refresh_tokens", "id", "bigint unsigned", true);

        assertColumn("personas", "dialogue_rules", "json", false);
        assertColumn("scenarios", "dialogue_rules", "json", false);
        assertColumn("reservations", "scenario_context", "text", false);
        assertColumn("reservations", "call_goal", "text", false);
        assertColumn("reservations", "scheduled_at_utc", "datetime(6)", false);
        assertColumn("call_sessions", "created_at", "datetime(6)", false);
        assertColumn("call_sessions", "ended_at", "datetime(6)", false);
        assertColumn("refresh_tokens", "token_hash", "varchar(64)", false);
        assertColumn("refresh_tokens", "expires_at", "datetime(6)", false);
    }

    @Test
    void enforcesUniqueExternalAccountsContentKeysAndReservationSessions() throws SQLException {
        execute("INSERT INTO users (status, created_at, updated_at) "
                + "VALUES ('ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))");
        long userId = queryLong("SELECT id FROM users LIMIT 1");
        execute("INSERT INTO user_login_accounts "
                + "(user_id, provider, provider_user_id, created_at, updated_at) "
                + "VALUES (" + userId + ", 'kakao', 'provider-user-1', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))");
        assertThatThrownBy(() -> execute("INSERT INTO user_login_accounts "
                + "(user_id, provider, provider_user_id, created_at, updated_at) "
                + "VALUES (" + userId + ", 'kakao', 'provider-user-1', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))"))
                .isInstanceOf(SQLException.class);

        long personaId = queryLong("SELECT id FROM personas WHERE `key` = 'santa'");
        assertThatThrownBy(() -> execute("INSERT INTO personas "
                + "(`key`, name, description, context_placeholder, default_instructions, dialogue_rules, "
                + "voice_id, image_url, preview_audio_url, status, created_at, updated_at) "
                + "VALUES ('santa', 'Duplicate Santa', 'duplicate', 'context', 'instructions', JSON_OBJECT(), "
                + "'duplicate-voice', '/duplicate.png', '/duplicate.mp3', 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))"))
                .isInstanceOf(SQLException.class);

        long scenarioId = queryLong("SELECT id FROM scenarios WHERE `key` = 'child-roleplay'");
        assertThatThrownBy(() -> execute("INSERT INTO scenarios "
                + "(`key`, name, description, context_placeholder, call_goal_placeholder, default_instructions, "
                + "dialogue_rules, status, created_at, updated_at) "
                + "VALUES ('child-roleplay', 'Duplicate scenario', 'duplicate', 'context', 'goal', "
                + "'instructions', JSON_OBJECT(), 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))"))
                .isInstanceOf(SQLException.class);

        execute("INSERT INTO reservations "
                + "(user_id, persona_id, scenario_id, scenario_context, call_goal, scheduled_at_utc, time_zone, "
                + "reservation_status, created_at, updated_at) VALUES (" + userId + ", " + personaId + ", "
                + scenarioId + ", 'context', 'goal', UTC_TIMESTAMP(6), 'Asia/Seoul', 'SCHEDULED', "
                + "UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))");
        long reservationId = queryLong("SELECT id FROM reservations LIMIT 1");
        execute("INSERT INTO call_sessions "
                + "(reservation_id, call_status, created_at, updated_at) VALUES (" + reservationId
                + ", 'PREPARING', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))");
        assertThatThrownBy(() -> execute("INSERT INTO call_sessions "
                + "(reservation_id, call_status, created_at, updated_at) VALUES (" + reservationId
                + ", 'PREPARING', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))"))
                .isInstanceOf(SQLException.class);
    }

    @Test
    void seedsIndependentMvpPersonasAndScenarios() throws SQLException {
        assertThat(queryValues("SELECT `key` FROM personas ORDER BY `key`"))
                .containsExactly("friend", "princess", "santa");
        assertThat(queryValues("SELECT `key` FROM scenarios ORDER BY `key`"))
                .containsExactly("child-roleplay", "go-home", "travel-friend-introduction");
        assertThat(queryLong("SELECT COUNT(*) FROM personas WHERE status = 'ACTIVE'"))
                .isEqualTo(3);
        assertThat(queryLong("SELECT COUNT(*) FROM scenarios WHERE status = 'ACTIVE'"))
                .isEqualTo(3);
        assertThat(queryLong("SELECT COUNT(*) FROM personas WHERE name IS NULL OR name = ''"))
                .isZero();
        assertThat(queryLong("SELECT COUNT(*) FROM scenarios WHERE call_goal_placeholder IS NULL "
                + "OR call_goal_placeholder = ''"))
                .isZero();
    }

    private static void assertColumn(String tableName, String columnName, String expectedType,
            boolean expectsAutoIncrement) throws SQLException {
        Map<String, String> columns = columnDefinitions(tableName);
        assertThat(columns).containsKey(columnName);
        String definition = columns.get(columnName);
        assertThat(definition).startsWith(expectedType + "|");
        assertThat(definition.contains("auto_increment")).isEqualTo(expectsAutoIncrement);
    }

    private static Set<String> tableNames() throws SQLException {
        return queryValues("SELECT table_name FROM information_schema.tables "
                + "WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE' "
                + "AND table_name <> 'flyway_schema_history'");
    }

    private static Set<String> columnNames(String tableName) throws SQLException {
        return queryValues("SELECT column_name FROM information_schema.columns "
                + "WHERE table_schema = DATABASE() AND table_name = '" + tableName + "'");
    }

    private static Map<String, String> columnDefinitions(String tableName) throws SQLException {
        Map<String, String> definitions = new LinkedHashMap<>();
        try (Connection connection = connection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("SELECT column_name, column_type, extra "
                        + "FROM information_schema.columns WHERE table_schema = DATABASE() "
                        + "AND table_name = '" + tableName + "'")) {
            while (resultSet.next()) {
                definitions.put(resultSet.getString("column_name"),
                        resultSet.getString("column_type") + "|" + resultSet.getString("extra"));
            }
        }
        return definitions;
    }

    private static Set<String> foreignKeyReferences() throws SQLException {
        return queryValues("SELECT CONCAT(table_name, '.', column_name, '->', referenced_table_name, '.', "
                + "referenced_column_name) FROM information_schema.key_column_usage "
                + "WHERE table_schema = DATABASE() AND referenced_table_name IS NOT NULL");
    }

    private static Set<String> queryValues(String sql) throws SQLException {
        Set<String> values = new LinkedHashSet<>();
        try (Connection connection = connection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                values.add(resultSet.getString(1));
            }
        }
        return values;
    }

    private static long queryLong(String sql) throws SQLException {
        try (Connection connection = connection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }

    private static void execute(String sql) throws SQLException {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
    }
}
