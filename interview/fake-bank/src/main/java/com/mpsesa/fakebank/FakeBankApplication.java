package com.mpsesa.fakebank;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/** Section IV fake bank. B1 every behaviour, B2 configurable rates, B3 exposes total paid. */
@SpringBootApplication
@EnableConfigurationProperties(BankProperties.class)
public class FakeBankApplication {
    public static void main(String[] args) {
        SpringApplication.run(FakeBankApplication.class, args);
    }
}
