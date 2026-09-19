package com.manarah.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/** Enables {@code @Async} (Spring Boot's applicationTaskExecutor runs it) for work that must not block a request, e.g. sending email. */
@Configuration
@EnableAsync
public class AsyncConfig {
}
