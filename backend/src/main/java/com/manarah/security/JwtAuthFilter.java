package com.manarah.security;

import com.manarah.common.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Cookie;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Validates the Bearer token, populates the security context and binds the tenant for the request. */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final com.manarah.academy.TeacherAcademyRepository academies;
    private final com.manarah.identity.repo.UserRepository users;

    public JwtAuthFilter(JwtService jwtService, com.manarah.academy.TeacherAcademyRepository academies, com.manarah.identity.repo.UserRepository users) {
        this.jwtService = jwtService;
        this.academies = academies;
        this.users = users;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        String token = null;
        if (header != null && header.startsWith("Bearer ")) {
            token = header.substring(7);
        } else if ("GET".equals(request.getMethod()) && request.getRequestURI().startsWith("/api/files/")) {
            // Media elements cannot set an Authorization header. A scoped HttpOnly cookie is
            // accepted only for read-only file requests; bearer tokens are never put in URLs.
            token = cookie(request, FileSessionCookie.COOKIE_NAME);
        }
        if (token != null) {
            try {
                UserPrincipal tokenPrincipal = jwtService.parse(token);
                var currentUser = users.findById(tokenPrincipal.getId())
                        .filter(u -> "ACTIVE".equals(u.getStatus()))
                        .orElseThrow();
                if (!jwtService.isCurrentFor(token, currentUser)) throw new IllegalArgumentException("Stale token");
                // Role, tenant and branch are authoritative database state, not stale JWT claims.
                UserPrincipal principal = UserPrincipal.from(currentUser);
                String scope = request.getHeader("X-Academy-Id");
                if (scope == null && request.getRequestURI().startsWith("/api/files/")) scope = request.getParameter("academy");
                if (scope != null && !scope.isBlank()) {
                    var academy = academies.findById(Long.valueOf(scope)).orElseThrow();
                    if (principal.isAdmin() && academy.getManagerTenantId().equals(principal.getTenantId())) {
                        Long branchId = users.findById(academy.getTeacherId()).orElseThrow().getBranchId();
                        principal = new UserPrincipal(principal.getId(), academy.getTenantId(), branchId, principal.getFullName(), principal.getUsername(), principal.getRole());
                    } else if (!academy.getTenantId().equals(principal.getTenantId())) {
                        response.sendError(403, "Academy access denied"); return;
                    }
                }
                var auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(auth);
                TenantContext.set(principal.getTenantId());
            } catch (Exception ex) {
                SecurityContextHolder.clearContext();
                TenantContext.clear();
            }
        }
        try {
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private static String cookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies) if (name.equals(cookie.getName())) return cookie.getValue();
        return null;
    }
}
