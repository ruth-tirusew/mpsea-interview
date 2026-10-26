package com.mpsesa.interview.core;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "wallet")
public record WalletProperties(String instanceId, Bank bank, Idempotency idempotency) {
    public record Bank(String baseUrl, Duration submitTimeout) {
    }

    public record Idempotency(Duration retention) {
    }
}
