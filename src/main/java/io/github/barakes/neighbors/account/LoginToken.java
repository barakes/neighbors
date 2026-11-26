package io.github.barakes.neighbors.account;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One signed-in phone: the hash of its token, whose account it is, and until when. */
@Entity
@Table(name = "login_tokens")
public class LoginToken {

    @Id
    @Column(name = "token_hash", length = 64)
    private String tokenHash;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected LoginToken() {
        // for JPA
    }

    public LoginToken(String tokenHash, Long accountId, Instant expiresAt) {
        this.tokenHash = tokenHash;
        this.accountId = accountId;
        this.expiresAt = expiresAt;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Long getAccountId() {
        return accountId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
