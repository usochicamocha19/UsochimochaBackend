-- Módulo de mantenimiento de subestaciones — ejecución (Semana 1, hito H1, continuación de V29/V30).
--
-- Alcance del MVP: solo disciplina CIVIL captura desde el móvil. Civil no usa
-- mant_conjunto/mant_elemento (su "Actividad civil" ya nombra el objeto de la
-- intervención), así que esta migración NO crea mant_ejecucion_conjunto ni
-- mant_ejecucion_elemento todavía — se crean cuando se retome Eléctrico (ver
-- SUBESTACIONES_PENDIENTE.md).
--
-- resultado, tipo_mantenimiento y tipo_actividad quedan como VARCHAR + CHECK
-- (no como tabla de catálogo): son estados fijos del proceso, no listas que
-- crecen — mismo patrón que ya usa fuel_inventory.area_costo.

CREATE TABLE mant_ejecucion (
    id                     BIGSERIAL    PRIMARY KEY,
    fecha                  DATE         NOT NULL,
    mes_ejecucion          SMALLINT     NOT NULL CHECK (mes_ejecucion BETWEEN 1 AND 12),
    semana_ejecucion       SMALLINT     NOT NULL CHECK (semana_ejecucion BETWEEN 1 AND 4),
    usuario_id             BIGINT       NOT NULL REFERENCES users(id),
    estacion_id            BIGINT       NOT NULL REFERENCES mant_estacion(id),
    disciplina             VARCHAR(20)  NOT NULL CHECK (disciplina IN ('ELECTRICO', 'ELECTROMECANICO', 'CIVIL')),
    tipo_mantenimiento     VARCHAR(20)  NOT NULL CHECK (tipo_mantenimiento IN ('PREVENTIVO', 'CORRECTIVO', 'PREDICTIVO', 'NO_PROGRAMADO')),
    -- 4 valores fijos desde ya, no 3 + ALTER después: Eléctrico/Civil solo usan
    -- INSPECCION/MANTENIMIENTO/NO_PROGRAMADO, Electromecánico usa OTRO en vez
    -- de NO_PROGRAMADO en este campo. Decidido así el 2026-09-07.
    tipo_actividad         VARCHAR(20)  NOT NULL CHECK (tipo_actividad IN ('INSPECCION', 'MANTENIMIENTO', 'NO_PROGRAMADO', 'OTRO')),
    actividad_id           BIGINT       REFERENCES mant_actividad(id),      -- NULL si no vino de una cita del cronograma
    programacion_id        BIGINT       REFERENCES mant_programacion(id),  -- NULL si no vino de una cita del cronograma
    es_programada          BOOLEAN      NOT NULL,  -- derivado de la ruta tomada por el técnico, no un dropdown
    -- NULL cuando actividad_id IS NOT NULL. Distingue "trabajo real no planeado"
    -- (NO_PROGRAMADO) de "la actividad no encaja en el catálogo" (OTRO, candidata
    -- a nueva fila de mant_actividad) — antes de esto ambos casos eran indistinguibles.
    motivo_no_catalogado   VARCHAR(20)  CHECK (motivo_no_catalogado IN ('NO_PROGRAMADO', 'OTRO')),
    resultado              VARCHAR(30)  NOT NULL CHECK (resultado IN ('CONFORME', 'CON_HALLAZGOS', 'REQUIERE_INTERVENCION')),
    observaciones          TEXT         NOT NULL,
    descripcion_libre      TEXT,        -- requerido en la app cuando actividad_id IS NULL
    uuid_cliente           UUID         NOT NULL UNIQUE,  -- idempotencia offline (móvil)
    origen                 VARCHAR(10)  NOT NULL CHECK (origen IN ('movil', 'web'))
);

CREATE TABLE mant_evidencia (
    id              BIGSERIAL     PRIMARY KEY,
    ejecucion_id    BIGINT        NOT NULL REFERENCES mant_ejecucion(id),
    ruta_archivo    VARCHAR(500)  NOT NULL,
    nombre_original VARCHAR(255)  NOT NULL,
    hash_sha256     VARCHAR(64)   NOT NULL,
    tamano_bytes    BIGINT        NOT NULL,
    subido_en       TIMESTAMP     NOT NULL DEFAULT now()
);

CREATE INDEX idx_mant_ejecucion_estacion ON mant_ejecucion(estacion_id);
CREATE INDEX idx_mant_ejecucion_fecha ON mant_ejecucion(fecha);
CREATE INDEX idx_mant_ejecucion_programacion ON mant_ejecucion(programacion_id);
CREATE INDEX idx_mant_evidencia_ejecucion ON mant_evidencia(ejecucion_id);
