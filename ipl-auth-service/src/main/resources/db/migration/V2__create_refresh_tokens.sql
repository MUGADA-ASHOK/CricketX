CREATE TABLE refresh_tokens (
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT       NOT NULL REFERENCES users (id),
    jti              VARCHAR(100) NOT NULL,
    token_hash       VARCHAR(255) NOT NULL,
    created_at       TIMESTAMP    NOT NULL DEFAULT now(),
    expires_at       TIMESTAMP    NOT NULL,
    revoked_at       TIMESTAMP,
    replaced_by_jti  VARCHAR(100),
    device_info      VARCHAR(255),

    CONSTRAINT uk_refresh_tokens_jti UNIQUE (jti)
);
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);
