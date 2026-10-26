package com.mpsesa.interview.core;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Bulkhead for money-moving writes (transfer, reversal, payout create).
 *
 * Those endpoints return a Callable, so the Tomcat thread is released at once and the work waits in
 * this bounded executor instead. Only {@link #MONEY_WRITERS} transactions run at a time per instance,
 * which leaves connections in the 10-connection Hikari pool for the requests that must never queue
 * behind a hot wallet: the freeze, reads, callbacks and the payout worker. A burst of 300 transfers on
 * one wallet therefore cannot delay a freeze for more than a few row-lock hand-offs.
 */
@Configuration
public class AsyncConfig implements WebMvcConfigurer {
    static final int MONEY_WRITERS = 6;
    static final int QUEUE_CAPACITY = 2000;

    @Bean(name = "moneyWriteExecutor")
    ThreadPoolTaskExecutor moneyWriteExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(MONEY_WRITERS);
        ex.setMaxPoolSize(MONEY_WRITERS);
        ex.setQueueCapacity(QUEUE_CAPACITY);
        ex.setThreadNamePrefix("money-write-");
        ex.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        return ex;
    }

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setTaskExecutor(moneyWriteExecutor());
        configurer.setDefaultTimeout(60_000);
    }
}
