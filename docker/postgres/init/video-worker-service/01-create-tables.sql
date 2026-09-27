-- =============================================================================
-- FIAP-X: Video Worker Service
-- Base de dados: fiapx_video_worker
-- Descrição: Criação da tabela de vídeos/jobs para o worker de processamento
-- =============================================================================

CREATE TABLE IF NOT EXISTS videos (
    id UUID PRIMARY KEY,
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

CREATE INDEX IF NOT EXISTS idx_worker_videos_status ON videos(status);
CREATE INDEX IF NOT EXISTS idx_worker_videos_user_id ON videos(user_id);

COMMENT ON TABLE videos IS 'Espelho de estado e auditoria de jobs de conversão de vídeo processados pelo worker';
COMMENT ON COLUMN videos.id IS 'Identificador único do vídeo (UUID)';
COMMENT ON COLUMN videos.user_id IS 'UUID do usuário proprietário';
COMMENT ON COLUMN videos.status IS 'Status da conversão (PENDING, PROCESSING, FINISHED, ERROR)';
