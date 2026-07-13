package com.codeforge.ai.infrastructure.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("prod")
@RequiredArgsConstructor
public class ProductionSecurityStartupGuard implements ApplicationListener<ApplicationReadyEvent> {

    static final String DEFAULT_JWT_SECRET = "change-this-jwt-secret-change-this-jwt-secret";

    private final Environment environment;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        String jwtSecret = environment.getProperty("codeforge.security.jwt.secret", DEFAULT_JWT_SECRET);
        if (DEFAULT_JWT_SECRET.equals(jwtSecret)) {
            throw new IllegalStateException(
                    "Production startup blocked: set codeforge.security.jwt.secret (JWT_SECRET) to a non-default value");
        }
        log.info("Production security guard passed: JWT secret is not the development placeholder");
    }
}
