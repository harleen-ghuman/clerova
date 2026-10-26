package com.clerova.security;

import com.nimbusds.jose.JWSAlgorithm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Authenticates API requests with Supabase Auth access tokens (JWT bearer
 * tokens). Only the root and health endpoints are public.
 */
@Configuration
public class SecurityConfig {

    private static final String SUPABASE_AUDIENCE = "authenticated";

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/", "/api/health", "/error").permitAll()
                        // Authenticated by the webhook's own basic auth check
                        .requestMatchers(HttpMethod.POST, "/api/inbound/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/stripe/webhook").permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(unauthorizedEntryPoint())
                )
                .exceptionHandling(exceptions ->
                        exceptions.authenticationEntryPoint(unauthorizedEntryPoint())
                );

        return http.build();
    }

    private AuthenticationEntryPoint unauthorizedEntryPoint() {
        return (request, response, exception) -> {
            response.setStatus(401);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(
                    "{\"error\":\"UNAUTHORIZED\",\"message\":\"Please sign in to continue.\"}"
            );
        };
    }

    /**
     * Verifies Supabase access tokens. Projects using Supabase's asymmetric
     * JWT signing keys are verified against the project's JWKS endpoint.
     * Projects still on the legacy shared secret set SUPABASE_JWT_SECRET.
     */
    @Bean
    public JwtDecoder jwtDecoder(
            @Value("${app.auth.supabase-url}") String supabaseUrl,
            @Value("${app.auth.jwt-secret:}") String jwtSecret
    ) {
        String baseUrl = supabaseUrl.replaceAll("/+$", "");

        NimbusJwtDecoder decoder = jwtSecret.isBlank()
                ? NimbusJwtDecoder
                        .withJwkSetUri(baseUrl + "/auth/v1/.well-known/jwks.json")
                        .jwsAlgorithm(SignatureAlgorithm.ES256)
                        .jwsAlgorithm(SignatureAlgorithm.RS256)
                        .build()
                : NimbusJwtDecoder
                        .withSecretKey(new SecretKeySpec(
                                jwtSecret.getBytes(StandardCharsets.UTF_8),
                                JWSAlgorithm.HS256.getName()
                        ))
                        .macAlgorithm(MacAlgorithm.HS256)
                        .build();

        OAuth2TokenValidator<Jwt> audienceValidator = jwt ->
                jwt.getAudience() != null && jwt.getAudience().contains(SUPABASE_AUDIENCE)
                        ? OAuth2TokenValidatorResult.success()
                        : OAuth2TokenValidatorResult.failure(
                                new OAuth2Error("invalid_token", "Invalid token audience", null)
                        );

        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(baseUrl + "/auth/v1"),
                audienceValidator
        ));

        return decoder;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origin}") String allowedOrigin
    ) {
        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOrigins(List.of(allowedOrigin));
        configuration.setAllowedMethods(
                List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        );
        configuration.setAllowedHeaders(List.of("*"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);

        return source;
    }
}
