package io.strato.aiops.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class TimeConfig {
    /** TimeConfig의 systemClock 처리에 필요한 업무 로직을 수행한다. */
    @Bean
    Clock systemClock() {
        return Clock.systemUTC();
    }
}
