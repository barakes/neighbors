-- "סולם החוצה": each person's own small steps, from inside the room to going out.
-- The text of each step is encrypted by the server (AES-GCM) before it's stored here:
-- whoever reads this table sees only meaningless bytes.

CREATE TABLE ladder_steps (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- Deleting an account deletes its ladder with it.
    account_id     BIGINT  NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    step_order     INT     NOT NULL,
    text_encrypted BYTEA   NOT NULL,
    -- Once reached, a step stays reached: a hard day doesn't take it away.
    reached        BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX ladder_steps_account ON ladder_steps (account_id, step_order);
