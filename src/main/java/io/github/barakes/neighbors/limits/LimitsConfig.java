package io.github.barakes.neighbors.limits;

import java.time.Clock;
import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
public class LimitsConfig {

    private final AttemptLimiter loginFailures;
    private final AttemptLimiter signups;

    public LimitsConfig(Clock clock) {
        // Guessing passwords: 10 wrong ones in 15 minutes from one address, then wait.
        this.loginFailures = new AttemptLimiter(10, Duration.ofMinutes(15), clock);
        // Creating many accounts: 5 an hour from one address is more than any real person needs.
        this.signups = new AttemptLimiter(5, Duration.ofHours(1), clock);
    }

    @Bean
    public AttemptLimiter loginFailures() {
        return loginFailures;
    }

    @Bean
    public AttemptLimiter signups() {
        return signups;
    }

    /** Every 15 minutes: forget addresses that haven't tried anything lately. */
    @Scheduled(fixedRate = 15 * 60 * 1000)
    public void forgetOldAttempts() {
        loginFailures.forgetOld();
        signups.forgetOld();
    }
}
