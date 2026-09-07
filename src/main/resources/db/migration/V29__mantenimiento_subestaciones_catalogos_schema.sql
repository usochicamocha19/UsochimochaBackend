-- Módulo de mantenimiento de subestaciones — catálogos base (Semana 1, hito H1).
--
-- Alcance del MVP: solo disciplina CIVIL captura desde el móvil (pivote de alcance
-- confirmado 2026-09-04; Eléctrico fue la disciplina original del plan pero quedó
-- en espera junto con Electromecánico, ver PLAN_TRABAJO.md).
-- Civil NO usa mant_conjunto/mant_elemento (su "Actividad civil" ya nombra el objeto
-- de la intervención) — por eso mant_conjunto no se crea en esta migración todavía.
-- Eléctrico ya tiene su columna 'disciplina' lista: se habilita más adelante
-- insertando filas de actividad/elemento con captura_movil_habilitada = TRUE (y
-- creando mant_conjunto, que sí necesita), no reestructurando tablas existentes.
-- Electromecánico también se habilita insertando filas de catálogo, pero además
-- necesitará tablas de extensión propias para mant_ejecucion (alineación láser,
-- control de sellos/cordón, control de rodamientos) que no existen todavía en
-- ningún diseño vivo — no asumir que "insertar filas" basta para esa disciplina.
-- Ver SUBESTACIONES/BPMN/LEEME_BPMN.md para el diseño completo de los 8 flujos.
--
-- 'disciplina' se implementa como VARCHAR + CHECK (no como tabla de catálogo):
-- es un dato fijo de 3 valores que solo sirve para filtrar, no una entidad con
-- comportamiento propio.

CREATE TABLE mant_estacion (
    id               BIGSERIAL PRIMARY KEY,
    nombre           VARCHAR(120) NOT NULL UNIQUE,
    tipo             VARCHAR(20)  NOT NULL CHECK (tipo IN ('BOMBEO', 'COMPLEMENTARIA')),
    frecuencia_base  VARCHAR(20)  NOT NULL CHECK (frecuencia_base IN ('TRIMESTRAL', 'ANUAL')),
    status           BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE mant_actividad (
    id                        BIGSERIAL    PRIMARY KEY,
    nombre                    VARCHAR(200) NOT NULL,
    disciplina                VARCHAR(20)  NOT NULL CHECK (disciplina IN ('ELECTRICO', 'ELECTROMECANICO', 'CIVIL')),
    captura_movil_habilitada  BOOLEAN      NOT NULL DEFAULT FALSE,
    status                    BOOLEAN      NOT NULL DEFAULT TRUE,
    UNIQUE (nombre, disciplina)
);

-- mant_elemento: catálogo por disciplina (área), no por estación — lo eléctrico
-- y lo electromecánico manejan elementos distintos aunque compartan estación
-- física. 'provisional = TRUE' marca las filas que salieron de partir el texto
-- libre histórico y todavía no pasaron por la depuración del ingeniero (riesgo
-- R1 del plan) — no bloquea su uso, solo lo señala en la UI de catálogos.
CREATE TABLE mant_elemento (
    id           BIGSERIAL    PRIMARY KEY,
    nombre       VARCHAR(200) NOT NULL,
    disciplina   VARCHAR(20)  NOT NULL CHECK (disciplina IN ('ELECTRICO', 'ELECTROMECANICO', 'CIVIL')),
    provisional  BOOLEAN      NOT NULL DEFAULT TRUE,
    status       BOOLEAN      NOT NULL DEFAULT TRUE,
    UNIQUE (nombre, disciplina)
);

CREATE TABLE mant_programacion (
    id            BIGSERIAL PRIMARY KEY,
    anio          SMALLINT  NOT NULL,
    mes           SMALLINT  NOT NULL CHECK (mes BETWEEN 1 AND 12),
    estacion_id   BIGINT    NOT NULL REFERENCES mant_estacion(id),
    actividad_id  BIGINT    NOT NULL REFERENCES mant_actividad(id),
    status        BOOLEAN   NOT NULL DEFAULT TRUE,
    UNIQUE (anio, mes, actividad_id, estacion_id)
);

CREATE INDEX idx_mant_programacion_anio ON mant_programacion(anio);
CREATE INDEX idx_mant_elemento_disciplina ON mant_elemento(disciplina);
CREATE INDEX idx_mant_actividad_disciplina ON mant_actividad(disciplina);
