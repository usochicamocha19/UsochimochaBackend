package com.app.usochicamochabackend.substation.application.service;

import com.app.usochicamochabackend.auth.application.dto.UserPrincipal;
import com.app.usochicamochabackend.auth.infrastructure.entity.UserEntity;
import com.app.usochicamochabackend.auth.infrastructure.repository.UserRepositoryJpa;
import com.app.usochicamochabackend.exception.BadRequestException;
import com.app.usochicamochabackend.substation.application.dto.CumplimientoResponse;
import com.app.usochicamochabackend.substation.application.dto.EjecucionEditRequest;
import com.app.usochicamochabackend.substation.application.dto.EjecucionRequest;
import com.app.usochicamochabackend.substation.application.dto.EjecucionResponse;
import com.app.usochicamochabackend.substation.application.dto.EstacionResponse;
import com.app.usochicamochabackend.substation.application.dto.IndicadorEstacionResponse;
import com.app.usochicamochabackend.substation.application.dto.ProgramacionResponse;
import com.app.usochicamochabackend.substation.application.dto.ResumenActividadResponse;
import com.app.usochicamochabackend.substation.application.port.SubstationCatalogUseCase;
import com.app.usochicamochabackend.substation.application.port.SubstationEjecucionUseCase;
import com.app.usochicamochabackend.substation.application.port.SubstationIndicadoresUseCase;
import com.app.usochicamochabackend.substation.infrastructure.entity.ActividadEntity;
import com.app.usochicamochabackend.substation.infrastructure.entity.DisciplinaEntity;
import com.app.usochicamochabackend.substation.infrastructure.entity.EstacionEntity;
import com.app.usochicamochabackend.substation.infrastructure.entity.ProgramacionEntity;
import com.app.usochicamochabackend.substation.infrastructure.repository.ActividadRepository;
import com.app.usochicamochabackend.substation.infrastructure.repository.DisciplinaRepository;
import com.app.usochicamochabackend.substation.infrastructure.repository.EstacionRepository;
import com.app.usochicamochabackend.substation.infrastructure.repository.ProgramacionRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Prueba de integración real contra el esquema H2 (modo PostgreSQL). El perfil `test`
 * real (application-test.properties) corre con Flyway deshabilitado y
 * spring.jpa.hibernate.ddl-auto=create-drop: el esquema lo genera Hibernate a partir de
 * las entidades JPA, no las migraciones V29/V30/V34-V38. Por eso este @BeforeEach siembra
 * sus propios datos REALES (no inventados) vía los repositorios JPA — 23 estaciones, las
 * 9 actividades CIVIL capturables y su programación 2026 — transcritos 1:1 desde
 * V30__mantenimiento_subestaciones_catalogos_seed.sql (con la corrección de V35 ya
 * aplicada: en el V30 vigente en el repo las 9 CIVIL ya nacen con
 * captura_movil_habilitada=TRUE, V35 es un no-op sobre un seed nuevo). Ver también
 * prepararVistasH2SoloUnaVez() para cómo se resuelven las vistas SQL de V37 bajo H2.
 */
@SpringBootTest
@Transactional
@TestPropertySource(properties = "app.storage.uploads-root=${java.io.tmpdir}/subestaciones-test-uploads")
class SubstationServiceIntegrationTest {

    @Autowired
    private SubstationCatalogUseCase catalogUseCase;

    @Autowired
    private SubstationEjecucionUseCase ejecucionUseCase;

    @Autowired
    private SubstationIndicadoresUseCase indicadoresUseCase;

    @Autowired
    private UserRepositoryJpa userRepositoryJpa;

    @Autowired
    private EstacionRepository estacionRepository;

    @Autowired
    private ActividadRepository actividadRepository;

    @Autowired
    private DisciplinaRepository disciplinaRepository;

    @Autowired
    private ProgramacionRepository programacionRepository;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private EntityManager entityManager;

    private UserPrincipal usuario;

    /**
     * v_mant_cumplimiento / v_mant_indicadores_estacion / v_mant_resumen_actividad (V37)
     * son VIEWs reales en Postgres. Bajo H2 con ddl-auto=create-drop, Hibernate no sabe
     * que CumplimientoView/IndicadorEstacionView/ResumenActividadView (@Immutable,
     * @Table) mapean vistas — genera TABLAS físicas vacías con ese nombre igual que para
     * cualquier otra @Entity. Se reemplazan una sola vez por contexto de Spring, con una
     * conexión JDBC aparte (no la del EntityManager de la transacción del test, porque en
     * H2 el DDL hace commit inmediato y rompería el rollback esperado de un @Test si
     * corriera dentro de su transacción).
     */
    private static final AtomicBoolean VISTAS_H2_LISTAS = new AtomicBoolean(false);

    @BeforeEach
    void setUp() throws SQLException {
        prepararVistasH2SoloUnaVez();

        UserEntity user = userRepositoryJpa.save(UserEntity.builder()
                .username("tecnico.test")
                .fullName("Técnico de Prueba")
                .email("tecnico.test@example.com")
                .role("OPERARIO")
                .password("irrelevante")
                .status(true)
                .build());
        usuario = new UserPrincipal(user.getId(), user.getUsername());

        DisciplinaEntity civil = disciplinaRepository.save(DisciplinaEntity.builder().codigo("CIVIL").build());
        Map<String, EstacionEntity> estaciones = sembrarEstaciones();
        Map<String, ActividadEntity> actividades = sembrarActividadesCivilesCapturables(civil);
        sembrarProgramacionCivil2026(estaciones, actividades);
    }

    private void prepararVistasH2SoloUnaVez() throws SQLException {
        if (!VISTAS_H2_LISTAS.compareAndSet(false, true)) {
            return;
        }
        try (Connection conexion = dataSource.getConnection();
             Statement st = conexion.createStatement()) {
            st.execute("DROP TABLE IF EXISTS v_mant_resumen_actividad");
            st.execute("DROP TABLE IF EXISTS v_mant_indicadores_estacion");
            st.execute("DROP TABLE IF EXISTS v_mant_cumplimiento");

            // Copia literal de V37 (H2 2.x en modo PostgreSQL soporta COUNT(*) FILTER (WHERE ...)).
            st.execute("""
                    CREATE VIEW v_mant_cumplimiento AS
                    SELECT
                        p.id                AS programacion_id,
                        p.anio,
                        p.mes,
                        e.id                AS estacion_id,
                        e.nombre            AS estacion_nombre,
                        e.tipo              AS estacion_tipo,
                        a.id                AS actividad_id,
                        a.nombre            AS actividad_nombre,
                        d.codigo            AS disciplina,
                        COUNT(ej.id)        AS ejecutado,
                        (COUNT(ej.id) > 0)  AS cumple
                    FROM mant_programacion p
                    JOIN mant_estacion e ON e.id = p.estacion_id
                    JOIN mant_actividad a ON a.id = p.actividad_id
                    JOIN mant_disciplina d ON d.id = a.disciplina_id
                    LEFT JOIN mant_ejecucion ej ON ej.programacion_id = p.id
                    WHERE p.status = TRUE
                    GROUP BY p.id, p.anio, p.mes, e.id, e.nombre, e.tipo, a.id, a.nombre, d.codigo
                    """);

            st.execute("""
                    CREATE VIEW v_mant_indicadores_estacion AS
                    WITH cumplimiento AS (
                        SELECT estacion_id,
                               COUNT(*)                           AS programado,
                               COUNT(*) FILTER (WHERE cumple)     AS cumple,
                               COUNT(*) FILTER (WHERE NOT cumple) AS no_cumple
                        FROM v_mant_cumplimiento
                        GROUP BY estacion_id
                    ),
                    ejecuciones AS (
                        SELECT estacion_id,
                               COUNT(*) FILTER (WHERE es_programada)                    AS ejecutado_programado,
                               COUNT(*) FILTER (WHERE NOT es_programada)                AS ejecutado_no_programado,
                               COUNT(*) FILTER (WHERE tipo_actividad = 'MANTENIMIENTO') AS ejecutado_mantenimiento,
                               COUNT(*) FILTER (WHERE tipo_actividad = 'INSPECCION')    AS ejecutado_inspeccion,
                               COUNT(*)                                                  AS ejecutado_total
                        FROM mant_ejecucion
                        GROUP BY estacion_id
                    )
                    SELECT
                        e.id                                                              AS estacion_id,
                        e.nombre                                                          AS estacion_nombre,
                        e.tipo                                                            AS estacion_tipo,
                        COALESCE(c.programado, 0)                                         AS programado,
                        COALESCE(c.cumple, 0)                                             AS cumple,
                        COALESCE(c.no_cumple, 0)                                          AS no_cumple,
                        ROUND(100.0 * COALESCE(c.cumple, 0) / NULLIF(c.programado, 0), 1) AS porcentaje_cumplimiento,
                        COALESCE(ej.ejecutado_programado, 0)                              AS ejecutado_programado,
                        COALESCE(ej.ejecutado_no_programado, 0)                           AS ejecutado_no_programado,
                        COALESCE(ej.ejecutado_mantenimiento, 0)                           AS ejecutado_mantenimiento,
                        COALESCE(ej.ejecutado_inspeccion, 0)                              AS ejecutado_inspeccion,
                        COALESCE(ej.ejecutado_total, 0)                                   AS ejecutado_total
                    FROM mant_estacion e
                    LEFT JOIN cumplimiento c ON c.estacion_id = e.id
                    LEFT JOIN ejecuciones ej ON ej.estacion_id = e.id
                    WHERE e.status = TRUE
                    """);

            st.execute("""
                    CREATE VIEW v_mant_resumen_actividad AS
                    WITH cumplimiento AS (
                        SELECT actividad_id,
                               COUNT(*)                       AS programado,
                               COUNT(*) FILTER (WHERE cumple) AS cumple
                        FROM v_mant_cumplimiento
                        GROUP BY actividad_id
                    ),
                    ejecuciones AS (
                        SELECT actividad_id,
                               COUNT(*) FILTER (WHERE NOT es_programada)                AS ejecutado_no_programado,
                               COUNT(*) FILTER (WHERE tipo_actividad = 'MANTENIMIENTO') AS mantenimiento,
                               COUNT(*) FILTER (WHERE tipo_actividad = 'INSPECCION')    AS inspeccion,
                               COUNT(*)                                                  AS ejecutado_total
                        FROM mant_ejecucion
                        WHERE actividad_id IS NOT NULL
                        GROUP BY actividad_id
                    )
                    SELECT
                        a.id                                     AS actividad_id,
                        a.nombre                                 AS actividad_nombre,
                        d.codigo                                 AS disciplina,
                        COALESCE(c.programado, 0)                AS programado_anual,
                        COALESCE(ej.ejecutado_total, 0)          AS ejecutado_anual,
                        COALESCE(ej.ejecutado_no_programado, 0)  AS ejecutado_no_programado,
                        COALESCE(ej.mantenimiento, 0)            AS mantenimiento,
                        COALESCE(ej.inspeccion, 0)               AS inspeccion,
                        COALESCE(ej.ejecutado_total, 0)          AS ejecutado_total
                    FROM mant_actividad a
                    JOIN mant_disciplina d ON d.id = a.disciplina_id
                    LEFT JOIN cumplimiento c ON c.actividad_id = a.id
                    LEFT JOIN ejecuciones ej ON ej.actividad_id = a.id
                    WHERE a.status = TRUE
                    """);
        }
    }

    /** 23 estaciones reales — V30, hoja MAESTRO_ESTACIONES. Claves en MAYÚSCULAS para casar con los nombres usados abajo en la programación (igual que el UPPER(nombre) de V30). */
    private Map<String, EstacionEntity> sembrarEstaciones() {
        String[][] datos = {
                {"Ayalas", "BOMBEO", "TRIMESTRAL"},
                {"CLAN", "COMPLEMENTARIA", "ANUAL"},
                {"Cuche", "BOMBEO", "TRIMESTRAL"},
                {"Dren Ayalas", "BOMBEO", "TRIMESTRAL"},
                {"Dren chorrito", "BOMBEO", "TRIMESTRAL"},
                {"Dren Cuche", "BOMBEO", "TRIMESTRAL"},
                {"Dren Duitama", "BOMBEO", "TRIMESTRAL"},
                {"Dren Jardines", "BOMBEO", "TRIMESTRAL"},
                {"Dren Suescun", "BOMBEO", "TRIMESTRAL"},
                {"Dren Tocogua", "BOMBEO", "TRIMESTRAL"},
                {"Duitama", "BOMBEO", "TRIMESTRAL"},
                {"Fuente Salinas", "BOMBEO", "TRIMESTRAL"},
                {"Holanda", "BOMBEO", "TRIMESTRAL"},
                {"La Copa", "COMPLEMENTARIA", "ANUAL"},
                {"La Playa", "COMPLEMENTARIA", "ANUAL"},
                {"Las Vueltas", "BOMBEO", "TRIMESTRAL"},
                {"Ministerio", "BOMBEO", "TRIMESTRAL"},
                {"Monquira", "BOMBEO", "TRIMESTRAL"},
                {"Pantano de Vargas", "BOMBEO", "TRIMESTRAL"},
                {"San Rafael", "BOMBEO", "TRIMESTRAL"},
                {"Sede Administrativa", "COMPLEMENTARIA", "ANUAL"},
                {"Surba", "BOMBEO", "TRIMESTRAL"},
                {"Tibasosa", "BOMBEO", "TRIMESTRAL"},
        };
        Map<String, EstacionEntity> resultado = new HashMap<>();
        for (String[] fila : datos) {
            EstacionEntity guardada = estacionRepository.save(EstacionEntity.builder()
                    .nombre(fila[0])
                    .tipo(fila[1])
                    .frecuenciaBase(fila[2])
                    .status(true)
                    .build());
            resultado.put(fila[0].toUpperCase(), guardada);
        }
        return resultado;
    }

    /** Las 9 actividades CIVIL con captura_movil_habilitada=TRUE tras V30+V35 — V30 ya las trae así en el repo actual; V35 es la corrección idempotente para bases que tenían el seed viejo. */
    private Map<String, ActividadEntity> sembrarActividadesCivilesCapturables(DisciplinaEntity civil) {
        String[] nombres = {
                "Corrección hallazgos sede administrativa/ CLAN",
                "Inspección infraestructura presa La Copa y La Playa",
                "Inspección maquinaria amarilla",
                "Inspección sede administrativa/ CLAN",
                "Inspección vehículos y motocicletas",
                "Inspección y mantenimiento cerchas/pasos elevados/limpieza malezas (segun estado)",
                "Inspección y mantenimiento compuertas + limpieza pozos succión",
                "Pintura muros Estaciones (segun estado)",
                "Pintura puertas/ventanas/barandas Estaciones",
        };
        Map<String, ActividadEntity> resultado = new HashMap<>();
        for (String nombre : nombres) {
            ActividadEntity guardada = actividadRepository.save(ActividadEntity.builder()
                    .nombre(nombre)
                    .disciplina(civil)
                    .capturaMovilHabilitada(true)
                    .status(true)
                    .build());
            resultado.put(nombre, guardada);
        }
        return resultado;
    }

    /**
     * 65 citas CIVIL de 2026 — subconjunto real (transcrito 1:1) de las 309 filas de
     * mant_programacion sembradas por V30; incluye las 24 de "compuertas" (todo el año,
     * repartidas en varias estaciones — no solo Duitama) que
     * resumenPorActividad_devuelveLas9ActividadesCivilesConSuProgramacionAnual necesita
     * para programadoAnual()==24, y las 2 de Duitama en el mes 2 ("compuertas" y
     * "pintura puertas/ventanas/barandas") que usan los demás tests. No se siembran las
     * 244 filas ELECTRICO/ELECTROMECANICO de V30: ningún test las necesita (el MVP solo
     * captura CIVIL) y no sembrarlas no cambia ningún resultado verificado aquí.
     */
    private void sembrarProgramacionCivil2026(Map<String, EstacionEntity> estaciones, Map<String, ActividadEntity> actividades) {
        Object[][] citas = {
                {2, "SURBA", "Pintura puertas/ventanas/barandas Estaciones"},
                {2, "HOLANDA", "Pintura puertas/ventanas/barandas Estaciones"},
                {2, "PANTANO DE VARGAS", "Pintura puertas/ventanas/barandas Estaciones"},
                {2, "FUENTE SALINAS", "Pintura puertas/ventanas/barandas Estaciones"},
                {2, "AYALAS", "Pintura puertas/ventanas/barandas Estaciones"},
                {2, "DUITAMA", "Pintura puertas/ventanas/barandas Estaciones"},
                {3, "CUCHE", "Pintura puertas/ventanas/barandas Estaciones"},
                {3, "TIBASOSA", "Pintura puertas/ventanas/barandas Estaciones"},
                {3, "MINISTERIO", "Pintura puertas/ventanas/barandas Estaciones"},
                {3, "MONQUIRA", "Pintura puertas/ventanas/barandas Estaciones"},
                {3, "LAS VUELTAS", "Pintura puertas/ventanas/barandas Estaciones"},
                {3, "SAN RAFAEL", "Pintura puertas/ventanas/barandas Estaciones"},

                {7, "PANTANO DE VARGAS", "Pintura muros Estaciones (segun estado)"},
                {7, "FUENTE SALINAS", "Pintura muros Estaciones (segun estado)"},
                {8, "HOLANDA", "Pintura muros Estaciones (segun estado)"},
                {8, "SURBA", "Pintura muros Estaciones (segun estado)"},
                {9, "AYALAS", "Pintura muros Estaciones (segun estado)"},
                {9, "DUITAMA", "Pintura muros Estaciones (segun estado)"},
                {10, "CUCHE", "Pintura muros Estaciones (segun estado)"},
                {10, "SAN RAFAEL", "Pintura muros Estaciones (segun estado)"},
                {11, "LAS VUELTAS", "Pintura muros Estaciones (segun estado)"},
                {11, "TIBASOSA", "Pintura muros Estaciones (segun estado)"},
                {12, "MINISTERIO", "Pintura muros Estaciones (segun estado)"},
                {12, "MONQUIRA", "Pintura muros Estaciones (segun estado)"},

                {4, "HOLANDA", "Inspección y mantenimiento cerchas/pasos elevados/limpieza malezas (segun estado)"},
                {4, "PANTANO DE VARGAS", "Inspección y mantenimiento cerchas/pasos elevados/limpieza malezas (segun estado)"},
                {4, "FUENTE SALINAS", "Inspección y mantenimiento cerchas/pasos elevados/limpieza malezas (segun estado)"},
                {8, "DUITAMA", "Inspección y mantenimiento cerchas/pasos elevados/limpieza malezas (segun estado)"},
                {8, "LAS VUELTAS", "Inspección y mantenimiento cerchas/pasos elevados/limpieza malezas (segun estado)"},
                {8, "SAN RAFAEL", "Inspección y mantenimiento cerchas/pasos elevados/limpieza malezas (segun estado)"},
                {8, "AYALAS", "Inspección y mantenimiento cerchas/pasos elevados/limpieza malezas (segun estado)"},
                {12, "CUCHE", "Inspección y mantenimiento cerchas/pasos elevados/limpieza malezas (segun estado)"},
                {12, "TIBASOSA", "Inspección y mantenimiento cerchas/pasos elevados/limpieza malezas (segun estado)"},
                {12, "MINISTERIO", "Inspección y mantenimiento cerchas/pasos elevados/limpieza malezas (segun estado)"},
                {12, "MONQUIRA", "Inspección y mantenimiento cerchas/pasos elevados/limpieza malezas (segun estado)"},

                {1, "SURBA", "Inspección y mantenimiento compuertas + limpieza pozos succión"},
                {1, "HOLANDA", "Inspección y mantenimiento compuertas + limpieza pozos succión"},
                {1, "PANTANO DE VARGAS", "Inspección y mantenimiento compuertas + limpieza pozos succión"},
                {1, "FUENTE SALINAS", "Inspección y mantenimiento compuertas + limpieza pozos succión"},
                {2, "DUITAMA", "Inspección y mantenimiento compuertas + limpieza pozos succión"},
                {2, "LAS VUELTAS", "Inspección y mantenimiento compuertas + limpieza pozos succión"},
                {2, "SAN RAFAEL", "Inspección y mantenimiento compuertas + limpieza pozos succión"},
                {2, "AYALAS", "Inspección y mantenimiento compuertas + limpieza pozos succión"},
                {3, "CUCHE", "Inspección y mantenimiento compuertas + limpieza pozos succión"},
                {3, "TIBASOSA", "Inspección y mantenimiento compuertas + limpieza pozos succión"},
                {3, "MINISTERIO", "Inspección y mantenimiento compuertas + limpieza pozos succión"},
                {3, "MONQUIRA", "Inspección y mantenimiento compuertas + limpieza pozos succión"},
                {10, "SURBA", "Inspección y mantenimiento compuertas + limpieza pozos succión"},
                {10, "HOLANDA", "Inspección y mantenimiento compuertas + limpieza pozos succión"},
                {10, "PANTANO DE VARGAS", "Inspección y mantenimiento compuertas + limpieza pozos succión"},
                {10, "FUENTE SALINAS", "Inspección y mantenimiento compuertas + limpieza pozos succión"},
                {11, "DUITAMA", "Inspección y mantenimiento compuertas + limpieza pozos succión"},
                {11, "LAS VUELTAS", "Inspección y mantenimiento compuertas + limpieza pozos succión"},
                {11, "SAN RAFAEL", "Inspección y mantenimiento compuertas + limpieza pozos succión"},
                {11, "AYALAS", "Inspección y mantenimiento compuertas + limpieza pozos succión"},
                {12, "CUCHE", "Inspección y mantenimiento compuertas + limpieza pozos succión"},
                {12, "TIBASOSA", "Inspección y mantenimiento compuertas + limpieza pozos succión"},
                {12, "MINISTERIO", "Inspección y mantenimiento compuertas + limpieza pozos succión"},
                {12, "MONQUIRA", "Inspección y mantenimiento compuertas + limpieza pozos succión"},

                {6, "LA COPA", "Inspección infraestructura presa La Copa y La Playa"},
                {6, "LA PLAYA", "Inspección infraestructura presa La Copa y La Playa"},

                {5, "SEDE ADMINISTRATIVA", "Inspección sede administrativa/ CLAN"},
                {7, "CLAN", "Inspección sede administrativa/ CLAN"},

                {5, "SEDE ADMINISTRATIVA", "Corrección hallazgos sede administrativa/ CLAN"},
                {7, "CLAN", "Corrección hallazgos sede administrativa/ CLAN"},
        };

        for (Object[] cita : citas) {
            Integer mes = (Integer) cita[0];
            String estacionNombre = (String) cita[1];
            String actividadNombre = (String) cita[2];
            programacionRepository.save(ProgramacionEntity.builder()
                    .anio(2026)
                    .mes(mes)
                    .estacion(estaciones.get(estacionNombre))
                    .actividad(actividades.get(actividadNombre))
                    .status(true)
                    .build());
        }
    }

    @Test
    void listarEstaciones_devuelveLas23SembradasEnV30() {
        List<EstacionResponse> estaciones = catalogUseCase.listarEstaciones();
        assertEquals(23, estaciones.size());
        assertTrue(estaciones.stream().anyMatch(e -> e.nombre().equals("Duitama")));
    }

    @Test
    void listarActividadesCapturables_civilDevuelveLas9HabilitadasPorV35() {
        var actividades = catalogUseCase.listarActividadesCapturables("CIVIL");
        assertEquals(9, actividades.size());
        assertTrue(actividades.stream().anyMatch(a -> a.nombre().equals("Inspección maquinaria amarilla")));
    }

    @Test
    void listarProgramacion_encuentraLasCitasRealesDeDuitamaMes2() {
        // Duitama en 2026-02 tiene 2 citas CIVIL reales sembradas en V30: compuertas y pintura.
        Long estacionId = buscarEstacionIdPorNombre("Duitama");
        List<ProgramacionResponse> citas = catalogUseCase.listarProgramacion(estacionId, 2026, 2, "CIVIL");
        assertEquals(2, citas.size());
        assertTrue(citas.stream().anyMatch(c ->
                c.actividadNombre().equals("Inspección y mantenimiento compuertas + limpieza pozos succión")));
    }

    @Test
    void registrarEjecucion_desdeUnaCitaReal_marcaEsProgramadaYQuedaConsultable() {
        Long estacionId = buscarEstacionIdPorNombre("Duitama");
        var cita = catalogUseCase.listarProgramacion(estacionId, 2026, 2, "CIVIL").stream()
                .filter(c -> c.actividadNombre().equals("Inspección y mantenimiento compuertas + limpieza pozos succión"))
                .findFirst()
                .orElseThrow();

        EjecucionRequest request = new EjecucionRequest(
                LocalDate.of(2026, 2, 15), 2, 3, estacionId, "CIVIL",
                "PREVENTIVO", "MANTENIMIENTO",
                cita.actividadId(), cita.id(), null,
                "CONFORME", "Compuertas limpias y lubricadas.", null,
                UUID.randomUUID());

        EjecucionResponse guardada = ejecucionUseCase.registrarEjecucion(request, usuario);

        assertNotNull(guardada.id());
        assertTrue(guardada.esProgramada());
        assertEquals("Duitama", guardada.estacionNombre());
        assertEquals("tecnico.test", guardada.responsable());

        EjecucionResponse recuperada = ejecucionUseCase.obtenerEjecucion(guardada.id());
        assertEquals(guardada.id(), recuperada.id());
        assertTrue(recuperada.evidencias().isEmpty());
    }

    @Test
    void registrarEjecucion_actividadNoPrevista_exigeMotivoYDescripcion() {
        Long estacionId = buscarEstacionIdPorNombre("Duitama");

        EjecucionRequest sinMotivo = new EjecucionRequest(
                LocalDate.now(), 9, 1, estacionId, "CIVIL",
                "NO_PROGRAMADO", "MANTENIMIENTO",
                null, null, null,
                "CONFORME", "obs", null,
                UUID.randomUUID());

        assertThrows(BadRequestException.class, () -> ejecucionUseCase.registrarEjecucion(sinMotivo, usuario));

        EjecucionRequest completo = new EjecucionRequest(
                LocalDate.now(), 9, 1, estacionId, "CIVIL",
                "NO_PROGRAMADO", "MANTENIMIENTO",
                null, null, "NO_PROGRAMADO",
                "CONFORME", "obs", "Se pintó una baranda que se estaba oxidando.",
                UUID.randomUUID());

        EjecucionResponse guardada = ejecucionUseCase.registrarEjecucion(completo, usuario);
        assertFalse(guardada.esProgramada());
        assertNull(guardada.actividadNombre());
        assertEquals("NO_PROGRAMADO", guardada.motivoNoCatalogado());
    }

    @Test
    void registrarEjecucion_esIdempotentePorUuidCliente() {
        Long estacionId = buscarEstacionIdPorNombre("Duitama");
        UUID uuidCliente = UUID.randomUUID();
        EjecucionRequest request = new EjecucionRequest(
                LocalDate.now(), 9, 1, estacionId, "CIVIL",
                "NO_PROGRAMADO", "MANTENIMIENTO",
                null, null, "NO_PROGRAMADO",
                "CONFORME", "obs", "algo no catalogado",
                uuidCliente);

        EjecucionResponse primera = ejecucionUseCase.registrarEjecucion(request, usuario);
        EjecucionResponse segunda = ejecucionUseCase.registrarEjecucion(request, usuario);

        assertEquals(primera.id(), segunda.id());
    }

    @Test
    void agregarEvidencia_seGuardaYApareceEnElDetalle() throws Exception {
        Long estacionId = buscarEstacionIdPorNombre("Duitama");
        EjecucionRequest request = new EjecucionRequest(
                LocalDate.now(), 9, 1, estacionId, "CIVIL",
                "NO_PROGRAMADO", "INSPECCION",
                null, null, "NO_PROGRAMADO",
                "CON_HALLAZGOS", "obs", "revision de rutina",
                UUID.randomUUID());
        EjecucionResponse ejecucion = ejecucionUseCase.registrarEjecucion(request, usuario);
        assertTrue(ejecucion.evidenciaPendiente());

        MockMultipartFile foto = new MockMultipartFile("file", "foto.jpg", "image/jpeg", new byte[]{1, 2, 3, 4});
        var evidencia = ejecucionUseCase.agregarEvidencia(ejecucion.id(), foto);

        assertNotNull(evidencia.id());
        assertEquals("foto.jpg", evidencia.nombreOriginal());

        EjecucionResponse detalle = ejecucionUseCase.obtenerEjecucion(ejecucion.id());
        assertEquals(1, detalle.evidencias().size());
        assertFalse(detalle.evidenciaPendiente());
    }

    @Test
    void registrarEjecucion_rechazaTipoActividadOtroParaCivil() {
        Long estacionId = buscarEstacionIdPorNombre("Duitama");
        EjecucionRequest request = new EjecucionRequest(
                LocalDate.now(), 9, 1, estacionId, "CIVIL",
                "NO_PROGRAMADO", "OTRO",
                null, null, "NO_PROGRAMADO",
                "CONFORME", "obs", "algo",
                UUID.randomUUID());

        assertThrows(BadRequestException.class, () -> ejecucionUseCase.registrarEjecucion(request, usuario));
    }

    @Test
    void registrarEjecucion_rechazaMotivoNoCatalogadoOtroParaCivil() {
        Long estacionId = buscarEstacionIdPorNombre("Duitama");
        EjecucionRequest request = new EjecucionRequest(
                LocalDate.now(), 9, 1, estacionId, "CIVIL",
                "NO_PROGRAMADO", "MANTENIMIENTO",
                null, null, "OTRO",
                "CONFORME", "obs", "algo no catalogado",
                UUID.randomUUID());

        assertThrows(BadRequestException.class, () -> ejecucionUseCase.registrarEjecucion(request, usuario));
    }

    @Test
    void editarEjecucion_actualizaCamposYQuedaEnElHistorial() {
        Long estacionId = buscarEstacionIdPorNombre("Duitama");
        EjecucionRequest request = new EjecucionRequest(
                LocalDate.now(), 9, 1, estacionId, "CIVIL",
                "NO_PROGRAMADO", "INSPECCION",
                null, null, "NO_PROGRAMADO",
                "CON_HALLAZGOS", "obs original", "revision de rutina",
                UUID.randomUUID());
        EjecucionResponse creada = ejecucionUseCase.registrarEjecucion(request, usuario);

        var edicion = new EjecucionEditRequest(
                creada.fecha(), creada.mesEjecucion(), creada.semanaEjecucion(),
                "NO_PROGRAMADO", "INSPECCION", null, "NO_PROGRAMADO",
                "CONFORME", "obs corregida tras revisar de nuevo", "revision de rutina, sin novedades",
                "Se corrigió el resultado: se había marcado con hallazgos por error.");

        EjecucionResponse editada = ejecucionUseCase.editarEjecucion(creada.id(), edicion, usuario);

        assertEquals("CONFORME", editada.resultado());
        assertEquals("obs corregida tras revisar de nuevo", editada.observaciones());
        assertEquals(1, editada.ediciones().size());
        assertEquals("tecnico.test", editada.ediciones().get(0).usuario());
        assertTrue(editada.ediciones().get(0).motivo().contains("hallazgos por error"));
    }

    @Test
    void editarEjecucion_exigeMotivoDeAlMenos15Caracteres() {
        Long estacionId = buscarEstacionIdPorNombre("Duitama");
        EjecucionRequest request = new EjecucionRequest(
                LocalDate.now(), 9, 1, estacionId, "CIVIL",
                "NO_PROGRAMADO", "INSPECCION",
                null, null, "NO_PROGRAMADO",
                "CONFORME", "obs", "algo",
                UUID.randomUUID());
        EjecucionResponse creada = ejecucionUseCase.registrarEjecucion(request, usuario);

        var edicionCorta = new EjecucionEditRequest(
                creada.fecha(), creada.mesEjecucion(), creada.semanaEjecucion(),
                "NO_PROGRAMADO", "INSPECCION", null, "NO_PROGRAMADO",
                "CONFORME", "obs", "algo", "muy corto");

        assertThrows(BadRequestException.class, () -> ejecucionUseCase.editarEjecucion(creada.id(), edicionCorta, usuario));
    }

    @Test
    void obtenerEjecucionPorProgramacion_devuelveLaEjecucionDeEsaCita() {
        Long estacionId = buscarEstacionIdPorNombre("Duitama");
        var cita = catalogUseCase.listarProgramacion(estacionId, 2026, 2, "CIVIL").stream()
                .filter(c -> c.actividadNombre().equals("Inspección y mantenimiento compuertas + limpieza pozos succión"))
                .findFirst()
                .orElseThrow();

        EjecucionRequest request = new EjecucionRequest(
                LocalDate.of(2026, 2, 15), 2, 3, estacionId, "CIVIL",
                "PREVENTIVO", "MANTENIMIENTO",
                cita.actividadId(), cita.id(), null,
                "CONFORME", "Compuertas limpias y lubricadas.", null,
                UUID.randomUUID());
        EjecucionResponse creada = ejecucionUseCase.registrarEjecucion(request, usuario);

        EjecucionResponse encontrada = ejecucionUseCase.obtenerEjecucionPorProgramacion(cita.id());
        assertEquals(creada.id(), encontrada.id());
    }

    @Test
    void listarEjecuciones_filtraPorEstacionYFecha() {
        Long estacionId = buscarEstacionIdPorNombre("Duitama");
        EjecucionRequest request = new EjecucionRequest(
                LocalDate.of(2026, 3, 1), 3, 1, estacionId, "CIVIL",
                "NO_PROGRAMADO", "INSPECCION",
                null, null, "NO_PROGRAMADO",
                "CONFORME", "obs", "algo",
                UUID.randomUUID());
        ejecucionUseCase.registrarEjecucion(request, usuario);

        var pagina = ejecucionUseCase.listarEjecuciones(
                estacionId, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), PageRequest.of(0, 10));

        assertTrue(pagina.getTotalElements() >= 1);
        assertTrue(pagina.getContent().stream().allMatch(e -> e.estacionId().equals(estacionId)));
    }

    @Test
    void cumplimientoPorMes_reflejaLaEjecucionRecienRegistrada() {
        Long estacionId = buscarEstacionIdPorNombre("Duitama");
        var cita = catalogUseCase.listarProgramacion(estacionId, 2026, 2, "CIVIL").stream()
                .filter(c -> c.actividadNombre().equals("Inspección y mantenimiento compuertas + limpieza pozos succión"))
                .findFirst()
                .orElseThrow();

        List<CumplimientoResponse> antes = indicadoresUseCase.cumplimientoPorMes(2026, 2, "CIVIL");
        assertTrue(antes.stream().anyMatch(c -> c.programacionId().equals(cita.id()) && !c.cumple()));

        EjecucionRequest request = new EjecucionRequest(
                LocalDate.of(2026, 2, 15), 2, 3, estacionId, "CIVIL",
                "PREVENTIVO", "MANTENIMIENTO",
                cita.actividadId(), cita.id(), null,
                "CONFORME", "Compuertas limpias y lubricadas.", null,
                UUID.randomUUID());
        ejecucionUseCase.registrarEjecucion(request, usuario);

        // v_mant_cumplimiento es una vista sobre mant_ejecucion: Hibernate no sabe que
        // escribir en una tabla afecta las filas de una vista mapeada a otra entidad, así
        // que no la considera "sucia" para el auto-flush antes del próximo SELECT. Además,
        // el test comparte una sola sesión/persistence-context (@Transactional de clase),
        // así que sin este flush+clear el segundo query devolvería la misma instancia en
        // caché de primer nivel en vez de reflejar la fila recién insertada. En producción
        // esto no aplica: cada request HTTP usa su propia transacción/EntityManager.
        entityManager.flush();
        entityManager.clear();

        List<CumplimientoResponse> despues = indicadoresUseCase.cumplimientoPorMes(2026, 2, "CIVIL");
        CumplimientoResponse fila = despues.stream()
                .filter(c -> c.programacionId().equals(cita.id()))
                .findFirst()
                .orElseThrow();
        assertTrue(fila.cumple());
        assertEquals(1, fila.ejecutado());
    }

    @Test
    void cumplimientoPorEstacion_devuelveTodoElAnioDeUnaEstacion() {
        Long estacionId = buscarEstacionIdPorNombre("Duitama");
        List<CumplimientoResponse> citas = indicadoresUseCase.cumplimientoPorEstacion(estacionId, 2026, "CIVIL");
        assertFalse(citas.isEmpty());
        assertTrue(citas.stream().allMatch(c -> c.estacionId().equals(estacionId)));
    }

    @Test
    void indicadoresPorEstacion_incluyeLas23EstacionesConSuPorcentaje() {
        List<IndicadorEstacionResponse> indicadores = indicadoresUseCase.indicadoresPorEstacion();
        assertEquals(23, indicadores.size());
        assertTrue(indicadores.stream().anyMatch(i -> i.estacionNombre().equals("Duitama") && i.programado() > 0));
    }

    @Test
    void resumenPorActividad_devuelveLas9ActividadesCivilesConSuProgramacionAnual() {
        List<ResumenActividadResponse> resumen = indicadoresUseCase.resumenPorActividad("CIVIL");
        assertEquals(9, resumen.size());
        assertTrue(resumen.stream().anyMatch(r ->
                r.actividadNombre().equals("Inspección y mantenimiento compuertas + limpieza pozos succión")
                        && r.programadoAnual() == 24));
    }

    private Long buscarEstacionIdPorNombre(String nombre) {
        return catalogUseCase.listarEstaciones().stream()
                .filter(e -> e.nombre().equals(nombre))
                .map(EstacionResponse::id)
                .findFirst()
                .orElseThrow();
    }
}
