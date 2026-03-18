CREATE TABLE IF NOT EXISTS app_user (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO app_user (username, password_hash)
VALUES ('demo', '$2b$12$GijUREgKLuwjjwTA9rnh2eJkA2sKc8acTm1saBAullY4AjXm5gMIu')
ON CONFLICT (username) DO NOTHING;

