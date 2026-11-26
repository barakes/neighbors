-- Signing in gives the phone a random token. Only its SHA-256 hash is stored here,
-- so even someone who reads this table can't sign in as anyone.

CREATE TABLE login_tokens (
    token_hash VARCHAR(64) PRIMARY KEY,
    -- Deleting an account deletes its tokens with it.
    account_id BIGINT      NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX login_tokens_account ON login_tokens (account_id);
