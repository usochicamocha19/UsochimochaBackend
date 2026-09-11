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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Prueba de integración real contra el esquema H2 (modo PostgreSQL). El perfil `test`
 * real (application-test.properties) corre con Flyway deshabilitado y
 * spring.jpa.hibernate.ddl-auto=create-drop: el esquema lo genera Hibernate a partir de
 * las entidades JPA, no las migraciones V29/V30/V34-V38.
 *
 * <p>Este test es deliberadamente INDEPENDIENTE del contenido del seeder de producción
 * (V30__mantenimiento_subestaciones_catalogos_seed.sql): ese seeder todavía no está
 * terminado/definitivo — la siembra real de datos de producción se hará de forma manual
 * más adelante — así que la lógica de negocio que se prueba aquí no debe depender de
 * cuántas estaciones/actividades reales existan hoy ni de sus nombres. El
 * {@code @BeforeEach} siembra, vía los repositorios JPA, un fixture SINTÉTICO mínimo y
 * arbitrario (estaciones y actividades con nombres inventados tipo "Estación Test Uno");
 * cada {@code @Test} agrega encima la programación puntual que necesita para ejercitar su
 * camino de lógica. Las aserciones verifican comportamiento (filtrado, idempotencia,
 * cálculo de cumplimiento, etc.), no conteos que coincidan con el seed real. Ver también
 * prepararVistasH2SoloUnaVez() para cómo se resuelven las vistas SQL de V37 bajo H2 — eso
 * sí es infraestructura de test legítima, no tiene relación con el seeder.
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
    private DisciplinaEntity civil;

    /** Fixture base sintético: 3 estaciones y 2 actividades CIVIL capturables, con nombres inventados. */
    private EstacionEntity estacionUno;
    private EstacionEntity estacionDos;
    private EstacionEntity estacionTres;
    private ActividadEntity actividadUno;
    private ActividadEntity actividadDos;

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

        civil = disciplinaRepository.save(DisciplinaEntity.builder().codigo("CIVIL").build());

        estacionUno = crearEstacion("Estación Test Uno");
        estacionDos = crearEstacion("Estación Test Dos");
        estacionTres = crearEstacion("Estación Test Tres");

        actividadUno = crearActividadCivilCapturable("Actividad Civil Capturable Uno");
        actividadDos = crearActividadCivilCapturable("Actividad Civil Capturable Dos");
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

    // ---------------------------------------------------------------------
    // Helpers de fixture sintético (sin relación con el seeder real V30)
    // ---------------------------------------------------------------------

    private EstacionEntity crearEstacion(String nombre) {
        return estacionRepository.save(EstacionEntity.builder()
                .nombre(nombre)
                .tipo("BOMBEO")
                .frecuenciaBase("TRIMESTRAL")
                .status(true)
                .build());
    }

    private ActividadEntity crearActividadCivilCapturable(String nombre) {
        return actividadRepository.save(ActividadEntity.builder()
                .nombre(nombre)
                .disciplina(civil)
                .capturaMovilHabilitada(true)
                .status(true)
                .build());
    }

    private ProgramacionEntity programar(EstacionEntity estacion, ActividadEntity actividad, int anio, int mes) {
        return programacionRepository.save(ProgramacionEntity.builder()
                .anio(anio)
                .mes(mes)
                .estacion(estacion)
                .actividad(actividad)
                .status(true)
                .build());
    }

    // ---------------------------------------------------------------------
    // Tests
    // ---------------------------------------------------------------------

    @Test
    void listarEstaciones_devuelveTodasLasCreadas() {
        List<EstacionResponse> estaciones = catalogUseCase.listarEstaciones();
        assertEquals(3, estaciones.size());
        assertTrue(estaciones.stream().anyMatch(e -> e.nombre().equals(estacionUno.getNombre())));
        assertTrue(estaciones.stream().anyMatch(e -> e.nombre().equals(estacionDos.getNombre())));
        assertTrue(estaciones.stream().anyMatch(e -> e.nombre().equals(estacionTres.getNombre())));
    }

    @Test
    void listarActividadesCapturables_filtraPorDisciplinaYCapturaMovilHabilitada() {
        // Actividad CIVIL pero no capturable, y actividad capturable de otra disciplina:
        // ninguna de las dos debe aparecer en el filtro por CIVIL capturables.
        actividadRepository.save(ActividadEntity.builder()
                .nombre("Actividad Civil No Capturable")
                .disciplina(civil)
                .capturaMovilHabilitada(false)
                .status(true)
                .build());
        DisciplinaEntity otraDisciplina = disciplinaRepository.save(DisciplinaEntity.builder().codigo("ELECTRICO").build());
        actividadRepository.save(ActividadEntity.builder()
                .nombre("Actividad Eléctrica Capturable")
                .disciplina(otraDisciplina)
                .capturaMovilHabilitada(true)
                .status(true)
                .build());

        var actividades = catalogUseCase.listarActividadesCapturables("CIVIL");

        assertEquals(2, actividades.size());
        assertTrue(actividades.stream().anyMatch(a -> a.nombre().equals(actividadUno.getNombre())));
        assertTrue(actividades.stream().anyMatch(a -> a.nombre().equals(actividadDos.getNombre())));
        assertFalse(actividades.stream().anyMatch(a -> a.nombre().equals("Actividad Civil No Capturable")));
        assertFalse(actividades.stream().anyMatch(a -> a.nombre().equals("Actividad Eléctrica Capturable")));
    }

    @Test
    void listarProgramacion_filtraPorEstacionAnioMesYDisciplina() {
        programar(estacionUno, actividadUno, 2030, 5);
        programar(estacionUno, actividadDos, 2030, 5);
        programar(estacionUno, actividadUno, 2030, 6); // otro mes, no debe aparecer
        programar(estacionDos, actividadUno, 2030, 5); // otra estación, no debe aparecer

        List<ProgramacionResponse> citas = catalogUseCase.listarProgramacion(estacionUno.getId(), 2030, 5, "CIVIL");

        assertEquals(2, citas.size());
        assertTrue(citas.stream().anyMatch(c -> c.actividadNombre().equals(actividadUno.getNombre())));
        assertTrue(citas.stream().anyMatch(c -> c.actividadNombre().equals(actividadDos.getNombre())));
    }

    @Test
    void registrarEjecucion_desdeUnaCitaProgramada_marcaEsProgramadaYQuedaConsultable() {
        ProgramacionEntity cita = programar(estacionUno, actividadUno, 2030, 2);

        EjecucionRequest request = new EjecucionRequest(
                LocalDate.of(2030, 2, 15), 2, 3, estacionUno.getId(), "CIVIL",
                "PREVENTIVO", "MANTENIMIENTO",
                actividadUno.getId(), cita.getId(), null,
                "CONFORME", "Todo en orden.", null,
                UUID.randomUUID());

        EjecucionResponse guardada = ejecucionUseCase.registrarEjecucion(request, usuario);

        assertNotNull(guardada.id());
        assertTrue(guardada.esProgramada());
        assertEquals(estacionUno.getNombre(), guardada.estacionNombre());
        assertEquals("tecnico.test", guardada.responsable());

        EjecucionResponse recuperada = ejecucionUseCase.obtenerEjecucion(guardada.id());
        assertEquals(guardada.id(), recuperada.id());
        assertTrue(recuperada.evidencias().isEmpty());
    }

    @Test
    void registrarEjecucion_actividadNoPrevista_exigeMotivoYDescripcion() {
        EjecucionRequest sinMotivo = new EjecucionRequest(
                LocalDate.now(), 9, 1, estacionUno.getId(), "CIVIL",
                "NO_PROGRAMADO", "MANTENIMIENTO",
                null, null, null,
                "CONFORME", "obs", null,
                UUID.randomUUID());

        assertThrows(BadRequestException.class, () -> ejecucionUseCase.registrarEjecucion(sinMotivo, usuario));

        EjecucionRequest completo = new EjecucionRequest(
                LocalDate.now(), 9, 1, estacionUno.getId(), "CIVIL",
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
        UUID uuidCliente = UUID.randomUUID();
        EjecucionRequest request = new EjecucionRequest(
                LocalDate.now(), 9, 1, estacionUno.getId(), "CIVIL",
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
        EjecucionRequest request = new EjecucionRequest(
                LocalDate.now(), 9, 1, estacionUno.getId(), "CIVIL",
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
        EjecucionRequest request = new EjecucionRequest(
                LocalDate.now(), 9, 1, estacionUno.getId(), "CIVIL",
                "NO_PROGRAMADO", "OTRO",
                null, null, "NO_PROGRAMADO",
                "CONFORME", "obs", "algo",
                UUID.randomUUID());

        assertThrows(BadRequestException.class, () -> ejecucionUseCase.registrarEjecucion(request, usuario));
    }

    @Test
    void registrarEjecucion_rechazaMotivoNoCatalogadoOtroParaCivil() {
        EjecucionRequest request = new EjecucionRequest(
                LocalDate.now(), 9, 1, estacionUno.getId(), "CIVIL",
                "NO_PROGRAMADO", "MANTENIMIENTO",
                null, null, "OTRO",
                "CONFORME", "obs", "algo no catalogado",
                UUID.randomUUID());

        assertThrows(BadRequestException.class, () -> ejecucionUseCase.registrarEjecucion(request, usuario));
    }

    @Test
    void editarEjecucion_actualizaCamposYQuedaEnElHistorial() {
        EjecucionRequest request = new EjecucionRequest(
                LocalDate.now(), 9, 1, estacionUno.getId(), "CIVIL",
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
        EjecucionRequest request = new EjecucionRequest(
                LocalDate.now(), 9, 1, estacionUno.getId(), "CIVIL",
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
        ProgramacionEntity cita = programar(estacionUno, actividadUno, 2030, 2);

        EjecucionRequest request = new EjecucionRequest(
                LocalDate.of(2030, 2, 15), 2, 3, estacionUno.getId(), "CIVIL",
                "PREVENTIVO", "MANTENIMIENTO",
                actividadUno.getId(), cita.getId(), null,
                "CONFORME", "Todo en orden.", null,
                UUID.randomUUID());
        EjecucionResponse creada = ejecucionUseCase.registrarEjecucion(request, usuario);

        EjecucionResponse encontrada = ejecucionUseCase.obtenerEjecucionPorProgramacion(cita.getId());
        assertEquals(creada.id(), encontrada.id());
    }

    @Test
    void listarEjecuciones_filtraPorEstacionYFecha() {
        EjecucionRequest request = new EjecucionRequest(
                LocalDate.of(2030, 3, 1), 3, 1, estacionUno.getId(), "CIVIL",
                "NO_PROGRAMADO", "INSPECCION",
                null, null, "NO_PROGRAMADO",
                "CONFORME", "obs", "algo",
                UUID.randomUUID());
        ejecucionUseCase.registrarEjecucion(request, usuario);

        var pagina = ejecucionUseCase.listarEjecuciones(
                estacionUno.getId(), LocalDate.of(2030, 1, 1), LocalDate.of(2030, 12, 31), PageRequest.of(0, 10));

        assertTrue(pagina.getTotalElements() >= 1);
        assertTrue(pagina.getContent().stream().allMatch(e -> e.estacionId().equals(estacionUno.getId())));
    }

    @Test
    void cumplimientoPorMes_reflejaLaEjecucionRecienRegistrada() {
        ProgramacionEntity cita = programar(estacionUno, actividadUno, 2030, 2);

        List<CumplimientoResponse> antes = indicadoresUseCase.cumplimientoPorMes(2030, 2, "CIVIL");
        assertTrue(antes.stream().anyMatch(c -> c.programacionId().equals(cita.getId()) && !c.cumple()));

        EjecucionRequest request = new EjecucionRequest(
                LocalDate.of(2030, 2, 15), 2, 3, estacionUno.getId(), "CIVIL",
                "PREVENTIVO", "MANTENIMIENTO",
                actividadUno.getId(), cita.getId(), null,
                "CONFORME", "Todo en orden.", null,
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

        List<CumplimientoResponse> despues = indicadoresUseCase.cumplimientoPorMes(2030, 2, "CIVIL");
        CumplimientoResponse fila = despues.stream()
                .filter(c -> c.programacionId().equals(cita.getId()))
                .findFirst()
                .orElseThrow();
        assertTrue(fila.cumple());
        assertEquals(1, fila.ejecutado());
    }

    @Test
    void cumplimientoPorEstacion_devuelveSoloLasCitasDeEsaEstacion() {
        programar(estacionUno, actividadUno, 2030, 2);
        programar(estacionUno, actividadDos, 2030, 8);
        programar(estacionDos, actividadUno, 2030, 2); // otra estación, no debe aparecer

        List<CumplimientoResponse> citas = indicadoresUseCase.cumplimientoPorEstacion(estacionUno.getId(), 2030, "CIVIL");

        assertEquals(2, citas.size());
        assertTrue(citas.stream().allMatch(c -> c.estacionId().equals(estacionUno.getId())));
    }

    @Test
    void indicadoresPorEstacion_incluyeTodasLasEstacionesActivasConSuProgramado() {
        programar(estacionUno, actividadUno, 2030, 2);
        // estacionDos y estacionTres quedan sin programación: deben seguir apareciendo con programado=0.

        List<IndicadorEstacionResponse> indicadores = indicadoresUseCase.indicadoresPorEstacion();

        assertEquals(3, indicadores.size());
        assertTrue(indicadores.stream().anyMatch(
                i -> i.estacionNombre().equals(estacionUno.getNombre()) && i.programado() > 0));
        assertTrue(indicadores.stream().anyMatch(
                i -> i.estacionNombre().equals(estacionDos.getNombre()) && i.programado() == 0));
    }

    @Test
    void resumenPorActividad_devuelveLasActividadesCivilesConSuProgramacionAnual() {
        programar(estacionUno, actividadUno, 2030, 1);
        programar(estacionDos, actividadUno, 2030, 2);
        programar(estacionTres, actividadUno, 2030, 3);
        // actividadDos no tiene ninguna cita programada: debe aparecer con programadoAnual=0.

        List<ResumenActividadResponse> resumen = indicadoresUseCase.resumenPorActividad("CIVIL");

        assertEquals(2, resumen.size());
        assertTrue(resumen.stream().anyMatch(r ->
                r.actividadNombre().equals(actividadUno.getNombre()) && r.programadoAnual() == 3));
        assertTrue(resumen.stream().anyMatch(r ->
                r.actividadNombre().equals(actividadDos.getNombre()) && r.programadoAnual() == 0));
    }
}
