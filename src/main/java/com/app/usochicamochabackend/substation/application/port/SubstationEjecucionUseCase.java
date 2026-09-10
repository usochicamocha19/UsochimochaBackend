package com.app.usochicamochabackend.substation.application.port;

import com.app.usochicamochabackend.auth.application.dto.UserPrincipal;
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

    EvidenciaResponse agregarEvidencia(Long ejecucionId, MultipartFile file) throws IOException;

    EjecucionResponse obtenerEjecucion(Long id);

    Page<EjecucionResponse> listarEjecuciones(Long estacionId, LocalDate fechaInicio, LocalDate fechaFin, Pageable pageable);
}
