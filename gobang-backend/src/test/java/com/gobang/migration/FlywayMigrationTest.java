package com.gobang.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayMigrationTest {

    @Test
    void migratesAnEmptyDatabaseFromV1ToV5() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:gobang_migration;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "sa",
                "");

        var migrationResult = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Integer passwordLength = jdbc.queryForObject(
                """
                SELECT character_maximum_length
                FROM information_schema.columns
                WHERE table_name = 'user_info' AND column_name = 'password'
                """,
                Integer.class);
        String winsNullable = jdbc.queryForObject(
                """
                SELECT is_nullable
                FROM information_schema.columns
                WHERE table_name = 'score' AND column_name = 'wins'
                """,
                String.class);

        assertThat(migrationResult.migrationsExecuted).isEqualTo(5);
        assertThat(passwordLength).isEqualTo(255);
        assertThat(winsNullable).isEqualToIgnoringCase("NO");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'game_result'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'spring_session'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'active_game'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history", Integer.class))
                .isEqualTo(6); // schema creation marker + V1 through V5
    }
}
