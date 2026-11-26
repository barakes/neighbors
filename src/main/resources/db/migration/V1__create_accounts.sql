-- Accounts: only what's needed to sign in again. No email, no phone, no real name.
-- Whatever isn't collected can't leak.

CREATE TABLE accounts (
                          id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                          username      VARCHAR(30)  NOT NULL,
    -- BCrypt hash of the password. The password itself is never stored.
                          password_hash VARCHAR(100) NOT NULL
);

-- "Dana" and "dana" are the same name.
CREATE UNIQUE INDEX accounts_username_unique ON accounts (lower(username));