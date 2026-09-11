package com.app.usochicamochabackend.substation.application.port;

import com.app.usochicamochabackend.auth.application.dto.UserPrincipal;
import com.app.usochicamochabackend.substation.application.dto.EjecucionEditRequest;
import com.app.usochicamochabackend.substation.application.dto.EjecucionRequest;
import com.app.usochicamochabackend.substation.application.dto.EjecucionResponse;
import com.app.usochicamochabackend.substation.application.dto.EvidenciaResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;

public interface SubstationEjecucionUseCase {

    /** Idempotente por uuidCliente: si ya existe, retorna la ejecución existente sin duplicar. */
    EjecucionResponse registrarEjecucion(EjecucionRequest request, UserPrincipal usuario);

    /** Corrige una ejecución ya registrada; exige motivo y deja rastro en el historial. */
    EjecucionResponse editarEjecucion(Long id, EjecucionEditRequest request, UserPrincipal usuario);

    EvidenciaResponse agregarEvidencia(Long ejecucionId, MultipartFile file) throws IOException;

    EjecucionResponse obtenerEjecucion(Long id);

    /** Para "ver detalle" desde una cita del cronograma ya cumplida. */
    EjecucionResponse obtenerEjecucionPorProgramacion(Long programacionId);

    /**
     * Listado de ejecuciones. {@code estacionId} es opcional: si es null, trae de todas las
     * estaciones para el rango de fechas dado (mismo patrón que
     * {@link SubstationIndicadoresUseCase#cumplimientoPorMes}). {@code esProgramada} es
     * opcional: si es null, no filtra por ese campo.
     */
    Page<EjecucionResponse> listarEjecuciones(
            Long estacionId, LocalDate fechaInicio, LocalDate fechaFin, Boolean esProgramada, Pageable pageable);
}
