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

## Estado actual (2026-09-07, el más reciente — lee esto primero)

- **Base de datos:** `V29` a `V37` aplicadas y verificadas contra `usochicamocha_local`
  (real, no solo el H2 de test). Incluye `mant_disciplina` (tabla real, no VARCHAR+CHECK —
  decisión revertida el mismo día, ver [[subestaciones-mvp-architecture-decisions]]),
  `mant_ejecucion`/`mant_evidencia` ya sin `origen`/`hash_sha256`/`tamano_bytes`
  (se quitaron por no tener ningún consumidor real), y 3 vistas de indicadores
  (`V37`, ver sección propia abajo).
- **Backend:** paquete `substation` ya escrito (`application/dto|port|service`,
  `infrastructure/entity|repository`, `web`) — entidades, repos, `SubstationService`,
  `SubstationController` en `/api/v1/substation/**`. Endpoints: catálogos (estaciones,
  actividades por disciplina, cronograma por estación+mes) + registrar ejecución
  (idempotente por `uuid_cliente`) + subir evidencia + listar/detalle + 3 endpoints
  de indicadores de solo lectura sobre las vistas de `V37`.
- **Verificado con 12 tests de integración reales** (`SubstationServiceIntegrationTest`,
  corre contra `usochicamocha_local`, no datos inventados) + el suite completo del
  proyecto: `mvn clean test` → 409 tests, 0 fallos (no 411 — ese número de una nota
  anterior estaba inflado por reportes de tests ya borrados, ver [[flyway-git-status-gotcha]]
  si hace falta el detalle).
- **Pendiente:** todo el lado móvil (Room + Retrofit + pantallas Compose) y las vistas
  web (listado, detalle, cronograma, cumplimiento/indicadores) — nada de eso existe
  todavía; el backend de lectura para cumplimiento/indicadores ya está listo (`V37`).

## `V37` — vistas de indicadores/cumplimiento (reemplazan 3 hojas del Excel)

Se revisó `SUBESTACIONES/Copia de Indicadores de mantenimiento.xlsx` (las hojas
`CONTROL_CUMPLIMIENTO`, `INDICADORES`, `RESUMEN_ANUAL` — que el distrito llevaba a
mano) para decidir qué reciclar/mejorar antes de construir el "Cronograma"/dashboard
web. Se construyeron 3 vistas SQL equivalentes, calculadas en vivo en vez de
re-tabuladas manualmente, **no filtradas a una sola disciplina** (mant_programacion
ya trae el cronograma completo de las 3 disciplinas):

- **`v_mant_cumplimiento`** — una fila por cita de `mant_programacion`, con
  `ejecutado` (count de `mant_ejecucion.programacion_id`) y `cumple` (boolean).
  Equivale a `CONTROL_CUMPLIMIENTO`. Es la base de las otras dos.
- **`v_mant_indicadores_estacion`** — agregada por estación: `programado`/`cumple`/
  `no_cumple`/`porcentaje_cumplimiento`, más desglose `ejecutado_programado` vs.
  `ejecutado_no_programado` y `ejecutado_mantenimiento` vs. `ejecutado_inspeccion`.
  Equivale a `INDICADORES`.
- **`v_mant_resumen_actividad`** — lo mismo agrupado por actividad en vez de
  estación (para ver qué actividad se salta más en toda la red). Equivale a
  `RESUMEN_ANUAL`.

`DASH_ESTACIONES` (KPIs simples tipo "actividades programadas: 309") **no necesita
vista propia** — son agregados sobre `v_mant_cumplimiento`, se resuelven en el
front sumando la respuesta de `/indicadores/por-estacion` o con un endpoint
agregador simple si hace falta, no ameritan una 4ª vista. `DASH_CRITICIDAD`
("elementos más intervenidos por estación") queda **fuera de alcance**: usa
`mant_elemento`, que Civil no tiene.

Endpoints nuevos en `SubstationController` (puerto `SubstationIndicadoresUseCase`,
mismo `SubstationService`, sin controller/service separado):
- `GET /api/v1/substation/indicadores/cumplimiento?anio=&mes=&disciplina=` (todas
  las estaciones de un mes) o `?estacionId=&anio=&disciplina=` (una estación, todo
  el año — ignora `mes`).
- `GET /api/v1/substation/indicadores/por-estacion` (sin filtro de disciplina: la
  vista ya agrega todas las citas de la estación, cualquier disciplina).
- `GET /api/v1/substation/indicadores/por-actividad?disciplina=`.

Entidades de solo lectura: `CumplimientoView`/`IndicadorEstacionView`/
`ResumenActividadView` (`@Entity @Immutable` sobre las vistas, no tablas
editables). **Gotcha de test encontrado y documentado inline en
`SubstationServiceIntegrationTest`**: dentro de una sola transacción/persistence-
context (como el `@Transactional` de clase del test), escribir en `mant_ejecucion`
y luego leer `v_mant_cumplimiento` en el mismo método puede devolver datos viejos
— Hibernate no sabe que la vista depende de esa tabla, así que no la marca para
auto-flush, y el identity-map de primer nivel puede devolver la instancia cacheada
en vez de re-mapear la fila fresca. Se resuelve con `entityManager.flush()` +
`.clear()` entre la escritura y la lectura en el test. **No aplica en producción**:
cada request HTTP usa su propia transacción, así que un cliente real siempre ve el
commit anterior ya aplicado.

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
- `V35__corregir_captura_movil_civil_en_mant_actividad.sql` — corrige `mant_actividad`
  en bases que ya tenían `V30` aplicada con el seed viejo (Eléctrico=TRUE) antes del
  pivote a Civil. Ver [[flyway-git-status-gotcha]] en memoria: `V29`/`V30` sí llegaron a
  aplicarse a `usochicamocha_local` (base real local) antes de que se les corrigiera el
  contenido en el código fuente — Flyway no vuelve a correr una migración ya aplicada,
  así que hizo falta esta migración nueva en vez de re-editar `V30`.
- `V29`, `V30`, `V31`-`V35` ya comiteados y aplicados contra `usochicamocha_local`
  (verificado 2026-09-07, `flyway_schema_history` hasta versión 35, todo `SUCCESS`).

## Ya escrita: `V34__mantenimiento_subestaciones_ejecucion_schema.sql`

**Ya no está pendiente** — se escribió y se validó el 2026-09-07 (`mvn test`, 411 tests,
0 fallos, Flyway la aplica limpio contra la BD de test). Crea `mant_ejecucion` y
`mant_evidencia` con el diseño ya cerrado, más 3 índices (`estacion_id`, `fecha`,
`programacion_id` en `mant_ejecucion`; `ejecucion_id` en `mant_evidencia`). Un ajuste
sobre el DDL que traía esta nota: se agregó `CHECK` a `resultado` (`CONFORME` /
`CON_HALLAZGOS` / `REQUIERE_INTERVENCION`, los mismos 3 valores que ya usaba la spec de
WEB-01 para el semáforo) y a `origen` (`movil`/`web`) — mismo patrón que el resto de
columnas de estado fijo, el DDL original los había dejado como `VARCHAR` sin restricción.

Civil no usa `mant_ejecucion_conjunto`/`mant_ejecucion_elemento` — no se crearon en
esta migración, solo cuando se retome Eléctrico (ver abajo).

(Nota: `V31` a `V33` quedaron tomados por limpiezas de código muerto sin relación con
subestaciones (módulo `maintenance` huérfano, tabla `oil_analysis_sos` sin controlador, y
`oil_change_requirements`/columnas `id_requirement`/`percentage_used` — propuesta que
nunca se conectó) — ver esas migraciones si hace falta contexto. Un intento de borrar
`cat_areas`/`CatalogController`/`MarcaModeloController` como "código muerto" se revirtió
el mismo día: el front sí los usa — `stores/data/catalog.js`, llamado desde
`MotoManagement.svelte`/`VehicleManagement.svelte` — la búsqueda que los marcó como
muertos solo cubrió `front/.../src/`, no la raíz real del proyecto donde viven
`stores/`/`components/`. Ver [[memoria]] de esta sesión si hace falta el detalle.)

DDL completo: ver el archivo real
`src/main/resources/db/migration/V34__mantenimiento_subestaciones_ejecucion_schema.sql`
— no se repite aquí para que esta nota no quede desactualizada si el schema cambia.

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

## Actualización 2026-09-09 — revisión contra el diseño móvil ("Formulario Civil en pasos")

Se importó el diseño de Claude Design del formulario móvil de 4 pasos (Contexto/
Actividad/Resultado/Evidencia) y se comparó campo a campo contra `EjecucionRequest` y
el resto del módulo `substation`. Coincidía casi todo; se cerraron 3 huecos y una
decisión de producto:

- **`V38__mant_ejecucion_edicion.sql`** — tabla nueva `mant_ejecucion_edicion`
  (`ejecucion_id`, `usuario_id`, `motivo`, `editado_en`). El diseño tiene una pantalla
  de detalle con botón "Editar registro" que exige un motivo y lo muestra en una línea
  de tiempo; antes de esto no había forma de editar una ejecución ya guardada ni de
  dejar rastro de por qué se editó. `mant_ejecucion` se sigue sobreescribiendo in-place
  (no se versiona entera), esta tabla solo guarda el rastro de auditoría.
- **`PUT /api/v1/substation/ejecuciones/{id}`** (`EjecucionEditRequest`) — corrige una
  ejecución existente. Exige `motivoEdicion` de al menos 15 caracteres (validado en
  `SubstationService.editarEjecucion`). **No** permite cambiar estación, disciplina,
  `programacionId` ni `uuidCliente` — editar es corregir lo reportado de la visita, no
  reasignarla. `EjecucionResponse` ahora trae `ediciones: List<EjecucionEdicionResponse>`
  (usuario, motivo, fecha) en orden cronológico.
- **`GET /api/v1/substation/ejecuciones/por-programacion/{programacionId}`** — antes no
  había forma de ir de una cita cumplida del cronograma (`programacionId`) a su
  ejecución real; `GET /ejecuciones` solo filtraba por estación+rango de fecha. Usa
  `EjecucionRepository.findFirstByProgramacion_IdOrderByIdDesc` (ya existía
  `findByProgramacion_Id` paginado, se agregó el método puntual).
- **Validaciones de negocio server-side**, antes solo se validaba
  "motivoNoCatalogado+descripcionLibre obligatorios si actividadId es null". Ahora
  `SubstationService` valida también, tanto al crear como al editar: `tipoMantenimiento`/
  `resultado`/`tipoActividad` contra los valores permitidos del `CHECK`, `observaciones`
  no vacío, y que `motivoNoCatalogado` sea `null` cuando sí hay `actividadId` (antes se
  ignoraba en silencio). `EjecucionResponse` trae un campo nuevo `evidenciaPendiente`
  (`resultado != CONFORME && evidencias.isEmpty()`) — el diseño exige foto salvo
  `CONFORME`, pero como la evidencia se sube en una llamada aparte después de crear la
  ejecución (offline-first, cada foto se reintenta sola), no se puede bloquear eso al
  crear sin romper esa resiliencia; este campo deja la señal disponible para que
  mobile/web marquen registros incompletos.
  **Fotos tomadas con cámara vs. subidas de galería**: no hace falta cambio, ya es
  agnóstico al origen — `EvidenciaStorageService` solo valida tipo MIME
  (jpeg/png/webp) y tamaño (15 MB), no exige metadata de cámara.
- **Decisión de producto (usuario, 2026-09-09): Civil no usa "OTRO" en ningún selector
  del formulario.** Había dos lugares con "Otro" en el diseño: (1) el selector "Tipo de
  actividad" del paso 2 traía un 4° chip "Otro" + texto libre `otroAct`, redundante con
  (2) el selector `motivo` (No programado/Otro) del flujo "no está en el catálogo". Se
  quitaron los dos del diseño (`Formulario Civil en pasos/Subestaciones Civil.dc.html`):
  `tipoActOpts` queda en 3 chips (Inspección/Mantenimiento/No programado), y el flujo
  "no está en el catálogo" ya no pregunta el motivo — queda fijo en `NO_PROGRAMADO` con
  la descripción libre directo. **El `CHECK` de la base de datos NO cambió** — sigue
  permitiendo `OTRO` en `tipo_actividad` y `motivo_no_catalogado` porque Electromecánico
  sí lo va a necesitar; la restricción a 3/1 valores para Civil vive solo en
  `SubstationService` (los sets `*_VALIDOS_CIVIL`), condicionada por disciplina.

**No se pudo correr `SubstationServiceIntegrationTest` en la sesión donde se hizo este
cambio** — esos tests conectan a un Postgres real (`usochicamocha_local`, no H2) y ese
sandbox no tenía Postgres disponible (`Connection to localhost:5432 refused`). El código
sí compila limpio (`./mvnw test-compile` → `BUILD SUCCESS`). **Antes de dar esto por
bueno, correr localmente**: `./mvnw test -Dtest=SubstationServiceIntegrationTest` contra
`usochicamocha_local` con Flyway habilitado (recoge `V38` sola). Se agregaron/ajustaron
tests: 2 casos rechazando `OTRO` para Civil (`tipoActividad` y `motivoNoCatalogado`), 2
de `editarEjecucion` (feliz + motivo corto rechazado), 1 de
`obtenerEjecucionPorProgramacion`, y se corrigieron 2 tests viejos que usaban
`motivoNoCatalogado="OTRO"` con disciplina CIVIL (ya no es válido).

## Actualización 2026-09-09 (continuación) — módulo Subestaciones en la app móvil

Se construyó el módulo completo en `movil/MobileApp_UsoChicamocha`, replicando la
arquitectura ya existente del proyecto (MVVM + Hilt, Room + Retrofit + WorkManager,
mismo patrón que "Form"/Inspección Maquinaria — UUID + lock atómico `isSyncing` para
idempotencia offline). Las 7 pantallas del diseño original
(`Formulario Civil en pasos/Subestaciones Civil.dc.html`) ya existen y compilan
(`./gradlew :app:compileDebugKotlin --offline` → `BUILD SUCCESSFUL` en cada hito):

- **Capa de datos** (`data/local/entity|dao`, `data/remote/dto`, `data/repository`,
  `domain/`): `EjecucionEntity`/`EjecucionDao` (tabla `pending_mant_ejecucion`,
  offline-first), `EstacionCacheEntity`/`ActividadCacheEntity` (catálogos cacheados
  para que el wizard funcione sin señal), `ImageEntity`/`ImageDao` extendidos con una
  3ª FK polimórfica `ejecucionUUID` (mismo patrón que ya usaba `vehicleInspectionUUID`
  junto a `formUUID`). Migración Room `MIGRATION_43_44` (version 44).
  `SubestacionRepository`/`SubestacionRepositoryImpl` + 11 casos de uso en
  `domain/usecase/subestacion/`.
- **Sincronización**: `SyncDataWorker` extendido con `SUBSTATION_ONLY`/
  `SUBSTATION_CATALOG`, `LocalSyncCoordinator.SyncTrigger.SubstationSaved`.
- **Pantallas**: `SubestacionHomeScreen` (hub, entrada desde un botón "Subestaciones"
  nuevo en el Home general), `CapturaScreen` (wizard de 4 pasos — Contexto/Actividad/
  Resultado/Evidencia — con cámara/galería reutilizando el patrón de
  `ui/form/FormScreen.kt`, **y modo edición**: un 5º sub-estado del paso 4 que pide
  `motivoEdicion` en vez de fotos), `CronogramaScreen` (citas del mes por estación),
  `PendientesScreen` (Vencidas/Realizadas/Por hacer, agrupado por mes),
  `DetalleScreen` (solo lectura + botón Editar + historial de ediciones + evidencias),
  `ColaScreen` (registros locales sin sincronizar + estado global de sync).

**Simplificaciones frente al mock del diseño** (documentadas también en el plan de
implementación de esa sesión, `/home/david/.claude/plans/swift-skipping-minsky.md`,
por si hace falta el detalle de cada decisión):
- Sin selector de `disciplina` en la UI — se fija `"CIVIL"` siempre, solo esa
  disciplina está habilitada en el backend.
- Cronograma, Pendientes y Detalle son **online-only** (consultan el backend en
  vivo, no se cachean en Room) — solo el catálogo de estaciones/actividades se
  cachea, para que el wizard de captura sí funcione sin señal. Es lo único
  genuinamente offline, que es lo que de verdad importa para un técnico en campo.
- **Editar una ejecución requiere red** (el botón "Guardar cambios" se deshabilita
  sin conexión) — coincide con que `PUT /ejecuciones/{id}` no tiene ruta offline; no
  hay "editar un borrador aún local" (tampoco existe para Form).
- **En modo edición no se agregan/quitan fotos** — solo se corrigen los campos de
  texto/selección; el paso 4 del wizard se reemplaza por el campo `motivoEdicion`
  en vez de mostrar cámara/galería. Si más adelante hace falta editar evidencia,
  el caso de uso de subir evidencia (`syncEvidencia`) ya existe y se puede
  reconectar a esa pantalla sin tocar el backend.
- Fotos: cámara y galería (intent-based, mismo patrón que `FormScreen.kt`) — no se
  agregó CameraX, no hay precedente en el proyecto y no hacía falta.

**Pendiente real para dar el módulo por terminado** (nada de esto se pudo hacer en
este sandbox, que no tiene emulador/dispositivo Android):
- Probar el flujo completo en un emulador/dispositivo real: capturar una visita sin
  señal, verificar que sincroniza sola al reconectar, subir evidencia, editar un
  registro ya sincronizado, y navegar Cronograma→Captura→Detalle→Editar de punta a
  punta.
- Verificar visualmente las miniaturas de evidencia en `DetalleScreen` contra un
  backend real corriendo (la URL se arma como
  `BuildConfig.BASE_URL` sin el sufijo `/api` + `/uploads/` + `rutaArchivo` — confirmar
  que coincide con `WebConfig.java` del backend, que sirve `/uploads/**` desde
  `file:uploads/`).
- No hay tests instrumentados/unitarios nuevos para el módulo móvil (el proyecto sí
  tiene infraestructura de test — MockK, Robolectric, Turbine, work-testing — pero no
  se usó en esta sesión por el tiempo disponible).
