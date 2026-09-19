package com.manarah;

import org.springframework.test.context.DynamicPropertyRegistry;

import java.nio.file.Path;

/**
 * Where an integration test class keeps its data. By default a throwaway SQLite file (no setup, used
 * with -Dspring.profiles.active=sqlite). Set MANARAH_TEST_PG_URL (plus _USER / _PASSWORD) and run with
 * -Dspring.profiles.active=postgres to run the same suite against PostgreSQL - the production database -
 * with each test class isolated in its own schema. SQLite is loosely typed and hides mistakes Postgres
 * rejects (e.g. an untyped null parameter in a LIKE), so the suite should regularly run both ways.
 */
public final class TestDatabase {
    private TestDatabase() {}

    public static void register(DynamicPropertyRegistry props, String run) {
        String pgUrl = System.getenv("MANARAH_TEST_PG_URL");
        if (pgUrl == null || pgUrl.isBlank()) {
            props.add("spring.datasource.url", () -> "jdbc:sqlite:" + Path.of("target", run + ".db").toAbsolutePath()
                    + "?foreign_keys=true&date_class=text&busy_timeout=5000");
            return;
        }
        String schema = run.toLowerCase().replaceAll("[^a-z0-9]", "_");
        props.add("spring.datasource.url", () -> pgUrl + (pgUrl.contains("?") ? "&" : "?") + "currentSchema=" + schema);
        props.add("spring.datasource.username", () -> System.getenv().getOrDefault("MANARAH_TEST_PG_USER", "manarah"));
        props.add("spring.datasource.password", () -> System.getenv().getOrDefault("MANARAH_TEST_PG_PASSWORD", ""));
        props.add("spring.flyway.schemas", () -> schema);
        props.add("spring.flyway.default-schema", () -> schema);
        props.add("spring.flyway.create-schemas", () -> "true");
    }
}
