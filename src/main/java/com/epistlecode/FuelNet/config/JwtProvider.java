package com.epistlecode.FuelNet.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Date;
import java.util.stream.Collectors;

/**
 * Issues and validates HS256 JWTs. The signing secret and lifetime come from
 * {@code app.jwt.*} in application.yml so they can differ per environment.
 */
@Component
public class JwtProvider {

    public static final String HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final SecretKey key;
    private final long expirationMs;

    public JwtProvider(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-ms:86400000}") long expirationMs
    ) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public String generateToken(Authentication auth) {
        Collection<? extends GrantedAuthority> authorities = auth.getAuthorities();
        String roles = authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(","));
        Date now = new Date();
        return Jwts.builder()
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .claim("email", auth.getName())
                .claim("authorities", roles)
                .signWith(key)
                .compact();
    }

    /** Parses and verifies a raw token (no "Bearer " prefix). Throws {@link JwtException} if invalid or expired. */
    public Claims parseClaims(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    /** Accepts either a bare token or an "Authorization: Bearer ..." header value. */
    public String getEmailFromJwtToken(String headerOrToken) {
        return String.valueOf(parseClaims(stripBearer(headerOrToken)).get("email"));
    }

    public static String stripBearer(String value) {
        if (value == null) return null;
        return value.startsWith(BEARER_PREFIX) ? value.substring(BEARER_PREFIX.length()) : value;
    }
}
