package com.mpsesa.interview.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mpsesa.interview.core.ErrorCode;
import com.mpsesa.interview.core.ErrorResponse;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

/**
 * Stateless. Callbacks: HMAC signature (bank only). Everything else: X-API-Key. Admin-only: /mpesa/internal/** and
 * wallet freeze/unfreeze. The filters are created here, not as beans, so Boot does not also register them globally.
 */
@Configuration
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfig {

    @Bean
    SecurityFilterChain chain(HttpSecurity http, ApiKeyRegistry registry, SecurityProperties props, Clock clock,
                              ObjectMapper json) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(Customizer.withDefaults())
                .authorizeHttpRequests(a -> a
                        .requestMatchers(CallbackSignatureFilter.PATH_PREFIX + "**").hasRole("BANK")
                        .requestMatchers("/mpesa/internal/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/mpesa/v1/wallets/*/status").hasRole("ADMIN")
                        .anyRequest().hasRole("USER"))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> write(res, json, ErrorCode.UNAUTHORIZED,
                                "valid credentials required"))
                        .accessDeniedHandler((req, res, ex) -> write(res, json, ErrorCode.FORBIDDEN,
                                "not allowed for this credential")))
                .addFilterBefore(new ApiKeyAuthFilter(registry), AuthorizationFilter.class)
                .addFilterBefore(new CallbackSignatureFilter(props.callback(), clock), AuthorizationFilter.class);
        return http.build();
    }

    private static void write(jakarta.servlet.http.HttpServletResponse res, ObjectMapper json, ErrorCode code,
                              String message) throws java.io.IOException {
        res.setStatus(code.status().value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        json.writeValue(res.getOutputStream(), new ErrorResponse(code.name(), message));
    }
}
