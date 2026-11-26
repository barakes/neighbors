package io.github.barakes.neighbors.account;

import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LoginTokenRepository extends JpaRepository<LoginToken, String> {

    long deleteByExpiresAtBefore(Instant time);
}
