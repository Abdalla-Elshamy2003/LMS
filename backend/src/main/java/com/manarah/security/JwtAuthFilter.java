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
    private final com.manarah.student.repo.StudentRepository students;
    private final com.manarah.identity.AccountBlocks blocks;
    private final com.fasterxml.jackson.databind.ObjectMapper json;

    public JwtAuthFilter(JwtService jwtService, com.manarah.academy.TeacherAcademyRepository academies, com.manarah.identity.repo.UserRepository users,
                         com.manarah.student.repo.StudentRepository students, com.manarah.identity.AccountBlocks blocks,
                         com.fasterxml.jackson.databind.ObjectMapper json) {
        this.jwtService = jwtService;
        this.academies = academies;
        this.users = users;
        this.students = students;
        this.blocks = blocks;
        this.json = json;
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
        String blocked = null;
        if (token != null) {
            try {
                UserPrincipal tokenPrincipal = jwtService.parse(token);
                var currentUser = users.findById(tokenPrincipal.getId())
                        .filter(u -> "ACTIVE".equals(u.getStatus()))
                        .orElseThrow();
                // A student's row in another teacher's space has no credentials of its own: its sessions are only
                // good while the account they sign in with is active and its password unchanged.
                var versionOwner = currentUser.getPrimaryUserId() == null ? currentUser
                        : users.findById(currentUser.getPrimaryUserId()).filter(u -> "ACTIVE".equals(u.getStatus())).orElseThrow();
                if (!jwtService.isCurrentFor(token, currentUser, versionOwner)) throw new IllegalArgumentException("Stale token");
                // A teacher removing a student ends that seat at once. Normally the whole login is archived with it;
                // when the student still has other teachers the login stays active, so the removed seat's own
                // sessions are refused here instead (the student's other teachers are unaffected).
                if (currentUser.getRole() == com.manarah.identity.domain.Role.STUDENT && currentUser.getPrimaryUserId() == null
                        && students.findByTenantIdAndUserId(currentUser.getTenantId(), currentUser.getId())
                            .map(s -> "ARCHIVED".equals(s.getStatus())).orElse(false)
                        && !users.findByPrimaryUserId(currentUser.getId()).isEmpty())
                    throw new IllegalArgumentException("Removed from this teacher");
                // A block takes effect on the very next request, not at the next sign-in. Asked only once the token is
                // known to be current, so a stale one never learns the reason.
                blocked = blocks.reason(currentUser, versionOwner);
                if (blocked != null) throw new IllegalStateException("Blocked");
                // Role, tenant and branch are authoritative database state, not stale JWT claims.
                UserPrincipal principal = UserPrincipal.from(currentUser);
                String scope = request.getHeader("X-Academy-Id");
                if (scope == null && request.getRequestURI().startsWith("/api/files/")) scope = request.getParameter("academy");
                if (scope != null && !scope.isBlank()) {
                    var academy = academies.findById(Long.valueOf(scope)).orElseThrow();
                    if (principal.isAdmin() && !academy.isArchived() && academy.getManagerTenantId().equals(principal.getTenantId())) {
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
        if (blocked != null) {
            // Written here, not through sendError, so the body (and its Arabic reason) reaches the app intact.
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setCharacterEncoding("UTF-8");
            response.setContentType("application/json;charset=UTF-8");
            json.writeValue(response.getWriter(), com.manarah.common.exception.GlobalExceptionHandler.ErrorResponse.of(
                    org.springframework.http.HttpStatus.FORBIDDEN, blocked,
                    java.util.Map.of("code", com.manarah.common.exception.ApiExceptions.AccountBlockedException.CODE)));
            return;
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
