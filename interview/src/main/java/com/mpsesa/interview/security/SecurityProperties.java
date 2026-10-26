package com.mpsesa.interview.security;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "wallet.security")
public record SecurityProperties(List<String> userKeys, List<String> adminKeys, Callback callback) {
    public record Callback(String secret, Duration tolerance) {
    }
}
