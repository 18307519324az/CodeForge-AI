package com.codeforge.ai.application.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "codeforge.release-gates")
public class ReleaseGatesProperties {

    /**
     * Release gate APIs are for local acceptance only. Disabled by default in all profiles.
     */
    private boolean enabled = false;
}
