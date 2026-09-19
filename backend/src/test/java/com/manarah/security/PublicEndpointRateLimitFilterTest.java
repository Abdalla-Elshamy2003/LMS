package com.manarah.security;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PublicEndpointRateLimitFilterTest {

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final PublicEndpointRateLimitFilter filter = new PublicEndpointRateLimitFilter(redis);

    private MockHttpServletResponse call(String method, String uri) throws Exception {
        var response = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest(method, uri), response, new MockFilterChain());
        return response;
    }

    private void redisCountIs(long count) {
        when(redis.opsForValue()).thenReturn(values);
        when(values.increment(anyString())).thenReturn(count);
    }

    @Test
    void verifyRouteIsThrottledByPrefixSoEveryTokenSharesOneBudget() throws Exception {
        redisCountIs(61);
        assertThat(call("GET", "/api/public/students/verify/" + "a".repeat(32)).getStatus()).isEqualTo(429);
    }

    @Test
    void requestsWithinTheBudgetPassThrough() throws Exception {
        redisCountIs(1);
        assertThat(call("GET", "/api/public/students/verify/" + "a".repeat(32)).getStatus()).isEqualTo(200);
    }

    @Test
    void unrelatedRoutesAreNeverCounted() throws Exception {
        assertThat(call("GET", "/api/public/landing").getStatus()).isEqualTo(200);
        assertThat(call("GET", "/api/courses").getStatus()).isEqualTo(200);
    }

    @Test
    void exactRulesDoNotMatchOtherMethodsOrLongerPaths() throws Exception {
        redisCountIs(99);
        assertThat(call("GET", "/api/auth/forgot-password").getStatus()).isEqualTo(200);
        assertThat(call("POST", "/api/auth/forgot-password/extra").getStatus()).isEqualTo(200);
        assertThat(call("POST", "/api/auth/forgot-password").getStatus()).isEqualTo(429);
    }
}
