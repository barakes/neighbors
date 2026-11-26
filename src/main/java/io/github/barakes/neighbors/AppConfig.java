package io.github.barakes.neighbors;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class AppConfig {

    /**
     * The time, as one bean, so tests can pass their own clock and move it forward
     * (for example, to check that a task disappears after 30 minutes without waiting 30 minutes).
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}