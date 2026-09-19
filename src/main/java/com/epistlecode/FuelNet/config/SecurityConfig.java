package com.epistlecode.FuelNet.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/**
 * Stateless JWT security.
 *
 * <ul>
 *   <li>Public: registration, login, read-only price/station data, homepage
 *       reference prices, Swagger UI and the health endpoint.</li>
 *   <li>Authenticated: the caller's own profile.</li>
 *   <li>ROLE_ADMIN: anything that writes prices or stations, and all of
 *       {@code /api/admin/**}. Method-level {@code @PreAuthorize} on the
 *       controllers backs up these URL rules.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtProvider jwtProvider;
    private final List<String> allowedOrigins;

    public SecurityConfig(JwtProvider jwtProvider,
                          @Value("${app.cors.allowed-origins}") List<String> allowedOrigins) {
        this.jwtProvider = jwtProvider;
        this.allowedOrigins = allowedOrigins;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // auth
                        .requestMatchers(HttpMethod.POST, "/api/user", "/api/user/login").permitAll()
                        // admin-only reads must come before the public GET wildcard
                        .requestMatchers(HttpMethod.GET, "/api/fuelPrice/audit").hasRole("ADMIN")
                        // public read-only data
                        .requestMatchers(HttpMethod.GET, "/api/fuelPrice/**", "/api/stations/**", "/api/homepage/**").permitAll()
                        // docs + health
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/actuator/health").permitAll()
                        // admin-only writes
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/fuelPrice/**", "/api/stations/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/fuelPrice/**", "/api/stations/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/fuelPrice/**", "/api/stations/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/fuelPrice/**", "/api/stations/**").hasRole("ADMIN")
                        // everything else under /api needs a valid token
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) -> writeError(res, HttpServletResponse.SC_UNAUTHORIZED,
                                "Unauthorized", "Authentication is required", req.getRequestURI()))
                        .accessDeniedHandler((req, res, e) -> writeError(res, HttpServletResponse.SC_FORBIDDEN,
                                "Forbidden", "You do not have permission to perform this action", req.getRequestURI()))
                )
                .addFilterBefore(new JwtTokenValidator(jwtProvider), BasicAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "Accept"));
        configuration.setExposedHeaders(List.of("Authorization"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private static void writeError(HttpServletResponse res, int status, String error, String message, String path)
            throws java.io.IOException {
        res.setStatus(status);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.getWriter().write("""
                {"timestamp":"%s","status":%d,"error":"%s","message":"%s","path":"%s"}
                """.formatted(Instant.now(), status, error, message, path).trim());
    }
}
