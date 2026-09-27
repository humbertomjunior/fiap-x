-- =============================================================================
-- FIAP-X: Notification Service
-- Base de dados: fiapx_notification
-- Descrição: Criação da tabela de histórico de notificações enviadas por e-mail
-- =============================================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE IF NOT EXISTS notification_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    user_email VARCHAR(255) NOT NULL,
    video_id UUID NOT NULL,
    status VARCHAR(255) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    message VARCHAR(2000) NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_notification_user_id ON notification_history(user_id);
CREATE INDEX IF NOT EXISTS idx_notification_user_created ON notification_history(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_notification_video_id ON notification_history(video_id);

COMMENT ON TABLE notification_history IS 'Registros históricos de notificações enviadas aos usuários';
COMMENT ON COLUMN notification_history.id IS 'Identificador único do registro de notificação (UUID)';
COMMENT ON COLUMN notification_history.user_id IS 'UUID do usuário destinatário';
COMMENT ON COLUMN notification_history.user_email IS 'E-mail para onde a notificação foi enviada';
COMMENT ON COLUMN notification_history.video_id IS 'UUID do vídeo referente à notificação';
COMMENT ON COLUMN notification_history.status IS 'Status do vídeo associado à notificação';
COMMENT ON COLUMN notification_history.subject IS 'Assunto do e-mail disparado';
COMMENT ON COLUMN notification_history.message IS 'Conteúdo da mensagem enviada';
COMMENT ON COLUMN notification_history.created_at IS 'Data/hora de envio da notificação';
