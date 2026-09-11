-- Historial de ediciones de una ejecución ya registrada.
--
-- El diseño del formulario móvil ("Formulario Civil en pasos") tiene una pantalla
-- de detalle con botón "Editar registro": exige un motivo (mínimo 15 caracteres) y
-- muestra ese motivo en una línea de tiempo debajo del registro. mant_ejecucion no
-- se versiona entera (los campos se sobreescriben in-place, igual que hoy), esta
-- tabla solo guarda el rastro de auditoría de cada edición: quién, cuándo, por qué.

CREATE TABLE mant_ejecucion_edicion (
    id            BIGSERIAL   PRIMARY KEY,
    ejecucion_id  BIGINT      NOT NULL REFERENCES mant_ejecucion(id),
    usuario_id    BIGINT      NOT NULL REFERENCES users(id),
    motivo        TEXT        NOT NULL,
    editado_en    TIMESTAMP   NOT NULL DEFAULT now()
);

CREATE INDEX idx_mant_ejecucion_edicion_ejecucion ON mant_ejecucion_edicion(ejecucion_id);
