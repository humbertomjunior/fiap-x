-- =============================================================================
-- FIAP-X: Auth Service
-- Base de dados: fiapx_auth
-- Descrição: Criação da tabela de usuários e credenciais para autenticação JWT
-- =============================================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_users_email UNIQUE (email)
);

CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);

COMMENT ON TABLE users IS 'Armazena credenciais e cadastro de usuários do FIAP-X';
COMMENT ON COLUMN users.id IS 'Identificador único do usuário (UUID)';
COMMENT ON COLUMN users.name IS 'Nome completo do usuário';
COMMENT ON COLUMN users.email IS 'Endereço de e-mail único utilizado para autenticação';
COMMENT ON COLUMN users.password IS 'Hash BCrypt da senha do usuário';
COMMENT ON COLUMN users.created_at IS 'Data e hora da criação do registro';
