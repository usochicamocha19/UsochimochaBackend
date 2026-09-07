# Módulo de Mantenimiento de Subestaciones — pendiente

Notas para quien retome este módulo. El desarrollo actual (práctica SENA, rama
`desarrollo-subestaciones-electricas` en los 3 repos) tiene alcance corto y termina
antes de que esto se complete — esto documenta lo ya construido, lo que falta, y las
decisiones ya tomadas para que no haya que volver a discutirlas.

Fuente de datos real: `SUBESTACIONES/Copia de Indicadores de mantenimiento.xlsx` y el
formulario real de Google Forms ("Inspección y Mantenimiento – Estaciones de Bombeo")
que usan hoy los técnicos — donde hay conflicto entre el Excel y el form, gana el form
(es lo que está en uso). Diagrama ER: `SUBESTACIONES/entidad relacion DISEÑO/
entidad_relacion_propuesta.drawio` — ya alineado con `V29`/`V30` (ver sección "Diagrama"
más abajo).

## Alcance actual: Civil

El plan original era Eléctrico primero; se cambió a **Civil** (confirmado, no es una
reversión posterior). Eléctrico y Electromecánico quedan diseñados pero sin capturar
desde el móvil todavía.

## Lo que ya existe

- `V29__mantenimiento_subestaciones_catalogos_schema.sql` — tablas de catálogo:
  `mant_estacion`, `mant_actividad`, `mant_elemento`, `mant_programacion`. **Deliberadamente
  no incluye `mant_conjunto`** (Civil no lo necesita — su "Actividad civil" ya nombra el
  objeto de la intervención, no hace falta un multi-select de conjuntos/elementos).
- `V30__mantenimiento_subestaciones_catalogos_seed.sql` — 23 estaciones, 16 actividades
  (las 9 de CIVIL con `captura_movil_habilitada = TRUE`), 59 elementos eléctricos
  provisionales (sin depurar, `provisional = TRUE`), 309 citas de cronograma 2026 de las
  3 disciplinas.
- Ambas migraciones sin aplicar todavía a una base real (no están en git, la raíz del
  repo no tiene `.git`).

## Lo que falta y sí bloquea el MVP: `V34`

Sin esto un técnico no tiene dónde guardar una visita. No escrito todavía, a propósito
(se dejó para quien continúe). Diseño ya cerrado, listo para pasar a SQL. (Nota: `V31`
a `V33` quedaron tomados por limpiezas de código muerto sin relación con subestaciones
(módulo `maintenance` huérfano, tabla `oil_analysis_sos` sin controlador, y
`oil_change_requirements`/columnas `id_requirement`/`percentage_used` — propuesta que
nunca se conectó) — ver esas migraciones si hace falta contexto. Un intento de borrar
`cat_areas`/`CatalogController`/`MarcaModeloController` como "código muerto" se revirtió
el mismo día: el front sí los usa — `stores/data/catalog.js`, llamado desde
`MotoManagement.svelte`/`VehicleManagement.svelte` — la búsqueda que los marcó como
muertos solo cubrió `front/.../src/`, no la raíz real del proyecto donde viven
`stores/`/`components/`. Ver [[memoria]] de esta sesión si hace falta el detalle.)

```sql
CREATE TABLE mant_ejecucion (
    id                     BIGSERIAL PRIMARY KEY,
    fecha                  DATE NOT NULL,
    mes_ejecucion          SMALLINT NOT NULL CHECK (mes_ejecucion BETWEEN 1 AND 12),
    semana_ejecucion       SMALLINT NOT NULL CHECK (semana_ejecucion BETWEEN 1 AND 4),
    usuario_id             BIGINT NOT NULL REFERENCES users(id),
    estacion_id            BIGINT NOT NULL REFERENCES mant_estacion(id),
    disciplina             VARCHAR(20) NOT NULL CHECK (disciplina IN ('ELECTRICO','ELECTROMECANICO','CIVIL')),
    tipo_mantenimiento     VARCHAR(20) NOT NULL CHECK (tipo_mantenimiento IN ('PREVENTIVO','CORRECTIVO','PREDICTIVO','NO_PROGRAMADO')),
    -- 4 valores fijos desde ya, no 3 + ALTER después: Eléctrico/Civil solo usan los
    -- primeros 3, Electromecánico usa OTRO en vez de NO_PROGRAMADO en este campo.
    -- Decidido así el 2026-09-07 para no tocar el CHECK más adelante.
    tipo_actividad         VARCHAR(20) NOT NULL CHECK (tipo_actividad IN ('INSPECCION','MANTENIMIENTO','NO_PROGRAMADO','OTRO')),
    actividad_id           BIGINT REFERENCES mant_actividad(id),      -- NULL si no vino de una cita
    programacion_id        BIGINT REFERENCES mant_programacion(id),   -- NULL si no vino de una cita
    es_programada           BOOLEAN NOT NULL,   -- derivado de la ruta tomada, no un dropdown
    motivo_no_catalogado   VARCHAR(20) CHECK (motivo_no_catalogado IN ('NO_PROGRAMADO','OTRO')),
    -- NULL cuando actividad_id IS NOT NULL. Distingue "trabajo real no planeado"
    -- (NO_PROGRAMADO) de "la actividad no encaja en el catálogo" (OTRO, candidata a
    -- nueva fila de mant_actividad). Antes de esto ambos casos eran indistinguibles.
    resultado              VARCHAR(30) NOT NULL,  -- ej. CONFORME/CON_HALLAZGOS/REQUIERE_INTERVENCION
    observaciones          TEXT NOT NULL,
    descripcion_libre      TEXT,   -- requerido cuando actividad_id IS NULL
    uuid_cliente           UUID NOT NULL UNIQUE,  -- idempotencia offline
    origen                 VARCHAR(10) NOT NULL   -- 'movil' / 'web'
);

CREATE TABLE mant_evidencia (
    id              BIGSERIAL PRIMARY KEY,
    ejecucion_id    BIGINT NOT NULL REFERENCES mant_ejecucion(id),
    ruta_archivo    VARCHAR(500) NOT NULL,
    nombre_original VARCHAR(255) NOT NULL,
    hash_sha256     VARCHAR(64) NOT NULL,
    tamano_bytes    BIGINT NOT NULL,
    subido_en       TIMESTAMP NOT NULL DEFAULT now()
);
```

Civil no usa `mant_ejecucion_conjunto`/`mant_ejecucion_elemento` — no hace falta crearlas
en este `V34`, solo cuando se retome Eléctrico (ver abajo).

## Habilitar Eléctrico o Electromecánico después: aditivo, no reestructuración

Con una excepción ya resuelta arriba (el `CHECK` de `tipo_actividad` ya trae los 4
valores desde `V34`, así que no hace falta un `ALTER` después) y una pendiente de
confirmar (equipos de Electromecánico, siguiente sección), todo lo demás es agregar,
no modificar:

- `UPDATE mant_actividad SET captura_movil_habilitada = TRUE WHERE disciplina = '...'`
  — sin `ALTER TABLE`.
- Crear `mant_conjunto` (no existe aún) + sembrar filas por disciplina.
- Crear `mant_ejecucion_conjunto` / `mant_ejecucion_elemento` (FK a `mant_ejecucion.id`,
  que para entonces ya existe) — mismo patrón para ambas: `(ejecucion_id, *_id nullable,
  texto_libre nullable)`.
- Electromecánico además necesita tablas de extensión propias para lo que capturó el
  form real y que el modelo genérico no cubre: **alineación láser** (desalineación
  horizontal/vertical inicial/final en mm, pata coja, resultado de tolerancia),
  **control de sellos/cordón plomajinado** (fechas de cambio, longitud, número de
  anillos, estado de instalación), **control de rodamientos** (equipo intervenido, tipo/
  referencia). Todas nuevas, con FK a `mant_ejecucion.id` — aditivo.

## Pendiente de confirmar con el ingeniero: identificación de equipos (Electromecánico)

El form real tiene un campo "Identificación del equipo" (Motor 1, Motor 2 ... Motor 6,
Motor/Bomba 75 HP) que no es un catálogo de *tipos* como `mant_conjunto`/`mant_elemento`
— parece ser identificación de una *instancia física* de equipo. No se sabe todavía si
esa numeración es igual en las 22 estaciones (lista fija genérica, como todo lo demás de
electromecánico) o si es específica por estación. Dos soluciones, según la respuesta:

**Si es una lista fija genérica** (igual en todas las estaciones): no hace falta tabla
nueva — se siembra como filas de `mant_elemento` con `disciplina = 'ELECTROMECANICO'`,
mismo patrón que ya existe. Cero cambio de estructura.

**Si es específica por estación**: crear una tabla nueva, **no** agregarle `estacion_id`
a `mant_conjunto`/`mant_elemento` (esa decisión de que sean solo por disciplina ya se
tomó y revirtió dos veces antes de asentarse — no reabrirla sin evidencia nueva):

```sql
CREATE TABLE mant_equipo (
    id           BIGSERIAL PRIMARY KEY,
    estacion_id  BIGINT NOT NULL REFERENCES mant_estacion(id),
    nombre       VARCHAR(100) NOT NULL,  -- 'Motor 1', 'Motor/Bomba 75 HP', etc.
    disciplina   VARCHAR(20) NOT NULL CHECK (disciplina IN ('ELECTRICO','ELECTROMECANICO','CIVIL')),
    status       BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE (estacion_id, nombre, disciplina)
);

CREATE TABLE mant_ejecucion_equipo (
    id           BIGSERIAL PRIMARY KEY,
    ejecucion_id BIGINT NOT NULL REFERENCES mant_ejecucion(id),
    equipo_id    BIGINT REFERENCES mant_equipo(id),
    texto_libre  VARCHAR(200)   -- "otro no catalogado", mismo patrón que conjunto/elemento
);
```

Ambos caminos son aditivos — ninguno toca `mant_conjunto`, `mant_elemento`, ni nada de lo
construido para Civil.

## Diagrama (drawio)

`entidad_relacion_propuesta.drawio` ya fue corregido (2026-09-07) para que coincida con
`V29`: se le quitaron `clave`/`codigo` (no existen en la tabla real), `tipo_mantenimiento`
en `mant_actividad` (solo vive en `mant_ejecucion`, es un dato de la ejecución, no del
catálogo), `cantidad_programada` en `mant_programacion` (cada cita es una fila, no un
contador), y se renombró `activo`→`status` en todas las tablas para que coincida con la
convención del proyecto. **Sigue faltando** agregarle la columna `provisional` a
`mant_elemento` en el diagrama (sí existe en `V29`) y las tablas de `V34` completas
(`mant_ejecucion` ya está dibujada con el campo viejo, falta agregarle
`motivo_no_catalogado`) — pendiente para quien edite el diagrama de nuevo.
