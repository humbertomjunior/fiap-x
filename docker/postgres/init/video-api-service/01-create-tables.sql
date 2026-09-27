-- =============================================================================
-- FIAP-X: Video API Service
-- Base de dados: fiapx_video
-- Descrição: Criação da tabela de vídeos e controle de status de processamento
-- =============================================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE IF NOT EXISTS videos (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    user_email VARCHAR(255) NOT NULL,
    title VARCHAR(255) NOT NULL,
    original_file_name VARCHAR(255) NOT NULL,
    original_storage_path VARCHAR(255) NOT NULL,
    zip_storage_path VARCHAR(255),
    status VARCHAR(255) NOT NULL,
    error_message VARCHAR(1000),
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_videos_user_id ON videos(user_id);
CREATE INDEX IF NOT EXISTS idx_videos_user_created ON videos(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_videos_status ON videos(status);

COMMENT ON TABLE videos IS 'Metadados e ciclo de vida de processamento de vídeos enviados pelos usuários';
COMMENT ON COLUMN videos.id IS 'Identificador único do vídeo (UUID)';
COMMENT ON COLUMN videos.user_id IS 'UUID do usuário proprietário do vídeo';
COMMENT ON COLUMN videos.user_email IS 'E-mail do proprietário para notificação';
COMMENT ON COLUMN videos.title IS 'Título ou identificador amigável do vídeo';
COMMENT ON COLUMN videos.original_file_name IS 'Nome original do arquivo submetido';
COMMENT ON COLUMN videos.original_storage_path IS 'Caminho físico no storage do vídeo original recebido';
COMMENT ON COLUMN videos.zip_storage_path IS 'Caminho físico no storage do pacote ZIP com os frames extraídos';
COMMENT ON COLUMN videos.status IS 'Status do ciclo de vida: PENDING, PROCESSING, FINISHED ou ERROR';
COMMENT ON COLUMN videos.error_message IS 'Mensagem descritiva de erro em caso de falha no processamento';
COMMENT ON COLUMN videos.created_at IS 'Data/hora do recebimento do upload';
COMMENT ON COLUMN videos.updated_at IS 'Data/hora da última atualização de status';
