package com.app.usochicamochabackend.substation.application.service;

import com.app.usochicamochabackend.auth.application.dto.UserPrincipal;
import com.app.usochicamochabackend.auth.infrastructure.entity.UserEntity;
import com.app.usochicamochabackend.auth.infrastructure.repository.UserRepositoryJpa;
import com.app.usochicamochabackend.exception.BadRequestException;
import com.app.usochicamochabackend.substation.application.dto.CumplimientoResponse;
import com.app.usochicamochabackend.substation.application.dto.EjecucionRequest;
import com.app.usochicamochabackend.substation.application.dto.EjecucionResponse;
import com.app.usochicamochabackend.substation.application.dto.EstacionResponse;
import com.app.usochicamochabackend.substation.application.dto.IndicadorEstacionResponse;
import com.app.usochicamochabackend.substation.application.dto.ProgramacionResponse;
import com.app.usochicamochabackend.substation.application.dto.ResumenActividadResponse;
import com.app.usochicamochabackend.substation.application.port.SubstationCatalogUseCase;
import com.app.usochicamochabackend.substation.application.port.SubstationEjecucionUseCase;
import com.app.usochicamochabackend.substation.application.port.SubstationIndicadoresUseCase;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Prueba de integración real contra el esquema H2 (modo PostgreSQL) con TODAS las
 * migraciones aplicadas de verdad (V29/V30/V34/V35 incluidas) — usa datos reales
 * sembrados (Duitama, mes 2/2026, "Inspección y mantenimiento compuertas + limpieza
 * pozos succión"), no datos inventados, para validar que el flujo completo de
 * captura Civil funciona de punta a punta sobre el esquema real.
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
    private EntityManager entityManager;

    private UserPrincipal usuario;

    @BeforeEach
    void setUp() {
        UserEntity user = userRepositoryJpa.save(UserEntity.builder()
                .username("tecnico.test")
                .fullName("Técnico de Prueba")
                .email("tecnico.test@example.com")
                .role("OPERARIO")
                .password("irrelevante")
                .status(true)
                .build());
        usuario = new UserPrincipal(user.getId(), user.getUsername());
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
                null, null, "OTRO",
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
                null, null, "OTRO",
                "CON_HALLAZGOS", "obs", "revision de rutina",
                UUID.randomUUID());
        EjecucionResponse ejecucion = ejecucionUseCase.registrarEjecucion(request, usuario);

        MockMultipartFile foto = new MockMultipartFile("file", "foto.jpg", "image/jpeg", new byte[]{1, 2, 3, 4});
        var evidencia = ejecucionUseCase.agregarEvidencia(ejecucion.id(), foto);

        assertNotNull(evidencia.id());
        assertEquals("foto.jpg", evidencia.nombreOriginal());

        EjecucionResponse detalle = ejecucionUseCase.obtenerEjecucion(ejecucion.id());
        assertEquals(1, detalle.evidencias().size());
    }

    @Test
    void listarEjecuciones_filtraPorEstacionYFecha() {
        Long estacionId = buscarEstacionIdPorNombre("Duitama");
        EjecucionRequest request = new EjecucionRequest(
                LocalDate.of(2026, 3, 1), 3, 1, estacionId, "CIVIL",
                "NO_PROGRAMADO", "INSPECCION",
                null, null, "OTRO",
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
