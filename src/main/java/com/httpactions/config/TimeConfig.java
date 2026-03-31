package com.httpactions.config;

import com.httpactions.service.Sleeper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.function.LongSupplier;

@Configuration
public class TimeConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public Sleeper sleeper() {
        return Thread::sleep;
    }

    @Bean
    public LongSupplier nanoTimeSupplier() {
        return System::nanoTime;
    }
}
