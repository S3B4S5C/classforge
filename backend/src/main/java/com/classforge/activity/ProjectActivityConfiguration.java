package com.classforge.activity;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import java.time.Clock;

@Configuration
@EnableScheduling
public class ProjectActivityConfiguration {
    @Bean("projectActivityClock")
    public Clock projectActivityClock() {
        return Clock.systemUTC();
    }
}
