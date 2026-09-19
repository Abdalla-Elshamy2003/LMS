package com.manarah;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

// Authentication is exclusively handled by Manarah's database-backed JWT filter. Excluding the
// default servlet user prevents Spring Boot from creating an unrelated fallback password/user.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableAsync
@EnableScheduling
public class ManarahApplication {
    public static void main(String[] args) {
        // The default local file-storage provider writes under ./data/files; make sure it exists.
        ensureDir("data/files");
        SpringApplication.run(ManarahApplication.class, args);
    }

    private static void ensureDir(String dir) {
        try {
            Files.createDirectories(Path.of(dir));
        } catch (IOException e) {
            throw new IllegalStateException("Unable to create data directory: " + dir, e);
        }
    }
}
