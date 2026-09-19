package com.manarah;

import org.springframework.test.context.DynamicPropertyRegistry;

/**
 * Where an integration test class keeps its data: PostgreSQL, the production database, with each test
 * class isolated in its own schema. Point it at a server with MANARAH_TEST_PG_URL (default
 * jdbc:postgresql://localhost:5432/manarah) plus MANARAH_TEST_PG_USER / MANARAH_TEST_PG_PASSWORD.
 */
public final class TestDatabase {
    private TestDatabase() {}

    public static void register(DynamicPropertyRegistry props, String run) {
        String pgUrl = System.getenv().getOrDefault("MANARAH_TEST_PG_URL", "jdbc:postgresql://localhost:5432/manarah");
        String schema = run.toLowerCase().replaceAll("[^a-z0-9]", "_");
        props.add("spring.datasource.url", () -> pgUrl + (pgUrl.contains("?") ? "&" : "?") + "currentSchema=" + schema);
        props.add("spring.datasource.username", () -> System.getenv().getOrDefault("MANARAH_TEST_PG_USER", "manarah"));
        props.add("spring.datasource.password", () -> System.getenv().getOrDefault("MANARAH_TEST_PG_PASSWORD", ""));
        props.add("spring.flyway.schemas", () -> schema);
        props.add("spring.flyway.default-schema", () -> schema);
        props.add("spring.flyway.create-schemas", () -> "true");
    }
}
