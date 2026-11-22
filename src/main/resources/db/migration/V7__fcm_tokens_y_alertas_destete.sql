-- V7__fcm_tokens_y_alertas_destete.sql: tokens FCM por usuario/dispositivo y registro de alertas de destete ya enviadas

CREATE TABLE dispositivos_fcm (
    id UUID PRIMARY KEY,
    usuario_id UUID NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    dispositivo_id VARCHAR(100) NOT NULL,
    token VARCHAR(512) NOT NULL UNIQUE,
    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (usuario_id, dispositivo_id)
);

-- Una fila por cordero ya notificado: evita repetir la alerta de destete en ejecuciones posteriores
CREATE TABLE alertas_destete_enviadas (
    animal_id UUID PRIMARY KEY REFERENCES animales(id) ON DELETE CASCADE,
    enviada_en TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
