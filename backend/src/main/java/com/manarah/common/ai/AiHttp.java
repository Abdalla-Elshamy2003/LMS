package com.manarah.common.ai;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * HTTP client for the chat-completions services. Generation calls routinely run 10–40 s on the
 * larger models, well past the default request timeout that the rest of the app is happy with.
 */
public final class AiHttp {
    private AiHttp() {}

    public static RestClient client() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(240));
        return RestClient.builder().requestFactory(factory).build();
    }
}
