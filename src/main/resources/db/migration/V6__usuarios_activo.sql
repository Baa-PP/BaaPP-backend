-- V6__usuarios_activo.sql: habilitación/desactivación de cuentas (WORKER_BRANCH)

ALTER TABLE usuarios
    ADD COLUMN activo BOOLEAN NOT NULL DEFAULT TRUE;
