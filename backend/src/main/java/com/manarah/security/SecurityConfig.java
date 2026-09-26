package com.manarah.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;
import java.util.Arrays;

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final PublicEndpointRateLimitFilter publicEndpointRateLimitFilter;
    private final List<String> allowedOrigins;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter,
                          PublicEndpointRateLimitFilter publicEndpointRateLimitFilter,
                          @Value("${manarah.security.cors.allowed-origins:http://localhost:5173,http://127.0.0.1:5173}") String allowedOrigins,
                          @Value("${manarah.public-app-url:}") String publicAppUrl) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.publicEndpointRateLimitFilter = publicEndpointRateLimitFilter;
        List<String> origins = new java.util.ArrayList<>(Arrays.stream(allowedOrigins.split(","))
                .map(String::trim).filter(s -> !s.isBlank()).toList());
        // The site's own public address is always an allowed origin. Behind the proxy the backend sees plain http on an
        // internal host, so a browser POST from https://<site> counts as cross-origin; without this, moving to a new
        // domain (droos.com.co) silently rejected every login and form with 403 until the list was edited by hand.
        String own = originOf(publicAppUrl);
        if (own != null && !origins.contains(own)) origins.add(own);
        this.allowedOrigins = List.copyOf(origins);
    }

    /** "https://droos.com.co/anything" → "https://droos.com.co"; null when blank or not an absolute http(s) URL. */
    public static String originOf(String url) {
        if (url == null || url.isBlank()) return null;
        try {
            java.net.URI u = java.net.URI.create(url.trim());
            if (u.getScheme() == null || u.getHost() == null || !u.getScheme().toLowerCase().startsWith("http")) return null;
            return u.getScheme().toLowerCase() + "://" + u.getHost().toLowerCase() + (u.getPort() == -1 ? "" : ":" + u.getPort());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(c -> c.configurationSource(corsConfigurationSource()))
                .csrf(c -> c.disable())
                .httpBasic(c -> c.disable())
                .formLogin(c -> c.disable())
                .logout(c -> c.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/auth/login",
                                "/api/auth/forgot-password",
                                "/api/auth/reset-password",
                                "/api/public/**",
                                "/api/whatsapp/webhook",
                                "/api/payments/paymob/webhook",
                                "/actuator/health").permitAll()
                        // A browser may authenticate a media/file GET with the scoped HttpOnly
                        // file-session cookie issued at login. Mutating file routes stay protected.
                        .requestMatchers(HttpMethod.GET, "/api/files/**").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                                .hasRole("SUPER_ADMIN")
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e.authenticationEntryPoint((request, response, ex) -> {
                    response.setStatus(401);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"يلزم تسجيل الدخول\"}");
                }))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(publicEndpointRateLimitFilter, JwtAuthFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Academy-Id", "Accept", "Range"));
        config.setExposedHeaders(List.of("Content-Disposition", "Content-Range", "Accept-Ranges"));
        // API authentication uses an Authorization header. The only authentication cookie is
        // SameSite=Strict and scoped to read-only /api/files GETs, so cross-origin credentials
        // are deliberately disabled.
        config.setAllowCredentials(false);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
