-- ============================================================
-- V36 — Correcciones de diseño revisadas con el usuario el 2026-09-07:
--
-- 1) disciplina pasa de VARCHAR+CHECK repetido en mant_actividad/
--    mant_elemento/mant_ejecucion a una tabla real mant_disciplina +
--    FK. Decisión explícita del usuario tras discutir el tradeoff
--    (normalización vs. simplicidad de un CHECK de 3 valores fijos) —
--    revierte la decisión original documentada en V29 y en la memoria
--    de arquitectura de esta sesión. Ver [[subestaciones-mvp-architecture-decisions]].
-- 2) mant_evidencia.hash_sha256 se quita: se calculaba pero nada en el
--    sistema lo usaba para deduplicar ni para nada más — código muerto
--    desde que se escribió.
-- 3) mant_evidencia.tamano_bytes se quita: no se usa para nada hoy.
-- 4) mant_ejecucion.origen se quita: mientras no exista una ruta de
--    creación desde la web (las 3 vistas web comprometidas son de solo
--    lectura), esta columna solo tendría un valor posible ('movil')
--    para siempre en la práctica actual.
-- ============================================================

-- --- 1) mant_disciplina ---------------------------------------------------

CREATE TABLE mant_disciplina (
    id     BIGSERIAL PRIMARY KEY,
    codigo VARCHAR(20) NOT NULL UNIQUE
);

INSERT INTO mant_disciplina (codigo) VALUES ('ELECTRICO'), ('ELECTROMECANICO'), ('CIVIL');

-- mant_actividad: agregar FK, poblar desde el valor existente, quitar la columna vieja.
ALTER TABLE mant_actividad ADD COLUMN disciplina_id BIGINT REFERENCES mant_disciplina(id);
UPDATE mant_actividad a SET disciplina_id = d.id FROM mant_disciplina d WHERE d.codigo = a.disciplina;
ALTER TABLE mant_actividad ALTER COLUMN disciplina_id SET NOT NULL;
ALTER TABLE mant_actividad DROP CONSTRAINT mant_actividad_nombre_disciplina_key;
ALTER TABLE mant_actividad DROP CONSTRAINT mant_actividad_disciplina_check;
ALTER TABLE mant_actividad DROP COLUMN disciplina;
ALTER TABLE mant_actividad ADD CONSTRAINT mant_actividad_nombre_disciplina_id_key UNIQUE (nombre, disciplina_id);
DROP INDEX IF EXISTS idx_mant_actividad_disciplina;
CREATE INDEX idx_mant_actividad_disciplina_id ON mant_actividad(disciplina_id);

-- mant_elemento: mismo patrón.
ALTER TABLE mant_elemento ADD COLUMN disciplina_id BIGINT REFERENCES mant_disciplina(id);
UPDATE mant_elemento e SET disciplina_id = d.id FROM mant_disciplina d WHERE d.codigo = e.disciplina;
ALTER TABLE mant_elemento ALTER COLUMN disciplina_id SET NOT NULL;
ALTER TABLE mant_elemento DROP CONSTRAINT mant_elemento_nombre_disciplina_key;
ALTER TABLE mant_elemento DROP CONSTRAINT mant_elemento_disciplina_check;
ALTER TABLE mant_elemento DROP COLUMN disciplina;
ALTER TABLE mant_elemento ADD CONSTRAINT mant_elemento_nombre_disciplina_id_key UNIQUE (nombre, disciplina_id);
DROP INDEX IF EXISTS idx_mant_elemento_disciplina;
CREATE INDEX idx_mant_elemento_disciplina_id ON mant_elemento(disciplina_id);

-- mant_ejecucion: mismo patrón (tabla nueva, sin filas reales todavía — igual se hace
-- como migración correctiva, no editando V34, por la misma razón documentada en
-- [[flyway-git-status-gotcha]]: no asumir que "recién creada" significa "segura de editar").
ALTER TABLE mant_ejecucion ADD COLUMN disciplina_id BIGINT REFERENCES mant_disciplina(id);
UPDATE mant_ejecucion e SET disciplina_id = d.id FROM mant_disciplina d WHERE d.codigo = e.disciplina;
ALTER TABLE mant_ejecucion ALTER COLUMN disciplina_id SET NOT NULL;
ALTER TABLE mant_ejecucion DROP CONSTRAINT mant_ejecucion_disciplina_check;
ALTER TABLE mant_ejecucion DROP COLUMN disciplina;

-- --- 2), 3) mant_evidencia: quitar hash_sha256 y tamano_bytes -------------

ALTER TABLE mant_evidencia DROP COLUMN hash_sha256;
ALTER TABLE mant_evidencia DROP COLUMN tamano_bytes;

-- --- 4) mant_ejecucion: quitar origen --------------------------------------

ALTER TABLE mant_ejecucion DROP CONSTRAINT mant_ejecucion_origen_check;
ALTER TABLE mant_ejecucion DROP COLUMN origen;
