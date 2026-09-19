package com.epistlecode.FuelNet.config;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

/**
 * Reads the Bearer token (if any) on every request and populates the security
 * context. A malformed or expired token gets an immediate 401 with the standard
 * error body instead of an exception bubbling out of the filter chain.
 */
public class JwtTokenValidator extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;

    public JwtTokenValidator(JwtProvider jwtProvider) {
        this.jwtProvider = jwtProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(JwtProvider.HEADER);

        if (header != null && header.startsWith("Bearer ")) {
            String token = JwtProvider.stripBearer(header);
            try {
                Claims claims = jwtProvider.parseClaims(token);
                String email = String.valueOf(claims.get("email"));
                String authorities = String.valueOf(claims.get("authorities"));
                List<GrantedAuthority> auth = AuthorityUtils.commaSeparatedStringToAuthorityList(authorities);
                SecurityContextHolder.getContext()
                        .setAuthentication(new UsernamePasswordAuthenticationToken(email, null, auth));
            } catch (Exception e) {
                SecurityContextHolder.clearContext();
                writeUnauthorized(request, response, "Invalid or expired token");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private void writeUnauthorized(HttpServletRequest request, HttpServletResponse response, String message)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("""
                {"timestamp":"%s","status":401,"error":"Unauthorized","message":"%s","path":"%s"}
                """.formatted(Instant.now(), message, request.getRequestURI()).trim());
    }
}
