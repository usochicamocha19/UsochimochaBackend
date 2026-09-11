package com.app.usochicamochabackend.substation.web;

import com.app.usochicamochabackend.auth.application.dto.UserPrincipal;
import com.app.usochicamochabackend.substation.application.dto.ActividadResponse;
import com.app.usochicamochabackend.substation.application.dto.CumplimientoResponse;
import com.app.usochicamochabackend.substation.application.dto.EjecucionEditRequest;
import com.app.usochicamochabackend.substation.application.dto.EjecucionRequest;
import com.app.usochicamochabackend.substation.application.dto.EjecucionResponse;
import com.app.usochicamochabackend.substation.application.dto.EstacionResponse;
import com.app.usochicamochabackend.substation.application.dto.EvidenciaResponse;
import com.app.usochicamochabackend.substation.application.dto.IndicadorEstacionResponse;
import com.app.usochicamochabackend.substation.application.dto.ProgramacionResponse;
import com.app.usochicamochabackend.substation.application.dto.ResumenActividadResponse;
import com.app.usochicamochabackend.substation.application.port.SubstationCatalogUseCase;
import com.app.usochicamochabackend.substation.application.port.SubstationEjecucionUseCase;
import com.app.usochicamochabackend.substation.application.port.SubstationIndicadoresUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/substation")
@RequiredArgsConstructor
@Tag(name = "Subestaciones", description = "Mantenimiento de estaciones de bombeo e infraestructura complementaria (MVP: disciplina CIVIL).")
public class SubstationController {

    private final SubstationCatalogUseCase catalogUseCase;
    private final SubstationEjecucionUseCase ejecucionUseCase;
    private final SubstationIndicadoresUseCase indicadoresUseCase;

    @GetMapping("/estaciones")
    @Operation(summary = "Listar estaciones activas")
    public ResponseEntity<List<EstacionResponse>> listarEstaciones() {
        return ResponseEntity.ok(catalogUseCase.listarEstaciones());
    }

    @GetMapping("/actividades")
    @Operation(summary = "Listar actividades del catálogo habilitadas para captura móvil, por disciplina")
    public ResponseEntity<List<ActividadResponse>> listarActividades(@RequestParam String disciplina) {
        return ResponseEntity.ok(catalogUseCase.listarActividadesCapturables(disciplina));
    }

    @GetMapping("/programacion")
    @Operation(summary = "Citas del cronograma para una estación+mes+disciplina")
    public ResponseEntity<List<ProgramacionResponse>> listarProgramacion(
            @RequestParam Long estacionId,
            @RequestParam Integer anio,
            @RequestParam Integer mes,
            @RequestParam String disciplina) {
        return ResponseEntity.ok(catalogUseCase.listarProgramacion(estacionId, anio, mes, disciplina));
    }

    @PostMapping(path = "/ejecuciones", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Registrar una ejecución de mantenimiento", description = "Idempotente por uuidCliente: reenviar el mismo uuid no duplica el registro.")
    public ResponseEntity<EjecucionResponse> registrarEjecucion(
            @RequestBody EjecucionRequest request,
            Authentication authentication) throws URISyntaxException {
        UserPrincipal usuario = (UserPrincipal) authentication.getPrincipal();
        EjecucionResponse saved = ejecucionUseCase.registrarEjecucion(request, usuario);
        return ResponseEntity.created(new URI("/api/v1/substation/ejecuciones/" + saved.id())).body(saved);
    }

    @PutMapping(path = "/ejecuciones/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Corregir una ejecución ya registrada", description = "Exige motivoEdicion (mínimo 15 caracteres); queda en el historial del registro. No permite cambiar estación, disciplina ni la cita de programación asociada.")
    public ResponseEntity<EjecucionResponse> editarEjecucion(
            @PathVariable Long id,
            @RequestBody EjecucionEditRequest request,
            Authentication authentication) {
        UserPrincipal usuario = (UserPrincipal) authentication.getPrincipal();
        return ResponseEntity.ok(ejecucionUseCase.editarEjecucion(id, request, usuario));
    }

    @PostMapping(path = "/ejecuciones/{id}/evidencia", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Subir una foto de evidencia para una ejecución ya registrada")
    public ResponseEntity<EvidenciaResponse> agregarEvidencia(
            @PathVariable Long id,
            @RequestPart("file") MultipartFile file) throws IOException {
        return ResponseEntity.ok(ejecucionUseCase.agregarEvidencia(id, file));
    }

    @GetMapping("/ejecuciones/{id}")
    @Operation(summary = "Detalle de una ejecución, con sus evidencias")
    public ResponseEntity<EjecucionResponse> obtenerEjecucion(@PathVariable Long id) {
        return ResponseEntity.ok(ejecucionUseCase.obtenerEjecucion(id));
    }

    @GetMapping("/ejecuciones/por-programacion/{programacionId}")
    @Operation(summary = "Ejecución registrada para una cita del cronograma", description = "Para 'ver detalle' desde una cita ya marcada como cumplida en el cronograma.")
    public ResponseEntity<EjecucionResponse> obtenerEjecucionPorProgramacion(@PathVariable Long programacionId) {
        return ResponseEntity.ok(ejecucionUseCase.obtenerEjecucionPorProgramacion(programacionId));
    }

    @GetMapping("/ejecuciones")
    @Operation(summary = "Listado de ejecuciones por estación y rango de fecha")
    public ResponseEntity<Page<EjecucionResponse>> listarEjecuciones(
            @RequestParam Long estacionId,
            @RequestParam(required = false) LocalDate fechaInicio,
            @RequestParam(required = false) LocalDate fechaFin,
            Pageable pageable) {
        return ResponseEntity.ok(ejecucionUseCase.listarEjecuciones(estacionId, fechaInicio, fechaFin, pageable));
    }

    @GetMapping("/indicadores/cumplimiento")
    @Operation(summary = "Cumplimiento del cronograma",
            description = "Sin estacionId: todas las estaciones para un mes+año dado. Con estacionId: esa estación a lo largo del año (ignora mes).")
    public ResponseEntity<List<CumplimientoResponse>> cumplimiento(
            @RequestParam(required = false) Long estacionId,
            @RequestParam Integer anio,
            @RequestParam(required = false) Integer mes,
            @RequestParam String disciplina) {
        if (estacionId != null) {
            return ResponseEntity.ok(indicadoresUseCase.cumplimientoPorEstacion(estacionId, anio, disciplina));
        }
        return ResponseEntity.ok(indicadoresUseCase.cumplimientoPorMes(anio, mes, disciplina));
    }

    @GetMapping("/indicadores/por-estacion")
    @Operation(summary = "Resumen de cumplimiento por estación (% cumplimiento, desglose programado/no programado)")
    public ResponseEntity<List<IndicadorEstacionResponse>> indicadoresPorEstacion() {
        return ResponseEntity.ok(indicadoresUseCase.indicadoresPorEstacion());
    }

    @GetMapping("/indicadores/por-actividad")
    @Operation(summary = "Resumen anual por actividad, todas las estaciones")
    public ResponseEntity<List<ResumenActividadResponse>> resumenPorActividad(@RequestParam String disciplina) {
        return ResponseEntity.ok(indicadoresUseCase.resumenPorActividad(disciplina));
    }
}
