-- V5__refresh_tokens.sql: persistencia de refresh tokens (hash SHA-256) para rotación y revocación

CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY,
    usuario_id UUID NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expira_en TIMESTAMPTZ NOT NULL,
    revocado_en TIMESTAMPTZ,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_refresh_tokens_usuario ON refresh_tokens (usuario_id);
CREATE INDEX idx_refresh_tokens_expira ON refresh_tokens (expira_en) WHERE revocado_en IS NULL;
