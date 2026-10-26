package com.mpsesa.interview.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Authenticates user and admin endpoints by the X-API-Key header. A missing or unknown key leaves the request anonymous. */
class ApiKeyAuthFilter extends OncePerRequestFilter {
    static final String HEADER = "X-API-Key";

    private final ApiKeyRegistry registry;

    ApiKeyAuthFilter(ApiKeyRegistry registry) {
        this.registry = registry;
    }

    /** Money writes run as async Callables (see AsyncConfig); the async re-dispatch must authenticate again. */
    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith(CallbackSignatureFilter.PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        registry.find(req.getHeader(HEADER)).ifPresent(client -> {
            var roles = client.admin()
                    ? List.of(new SimpleGrantedAuthority("ROLE_USER"), new SimpleGrantedAuthority("ROLE_ADMIN"))
                    : List.of(new SimpleGrantedAuthority("ROLE_USER"));
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(client.id(), null, roles));
        });
        chain.doFilter(req, res);
    }
}
