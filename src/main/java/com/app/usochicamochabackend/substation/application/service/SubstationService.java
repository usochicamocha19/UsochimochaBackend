package com.app.usochicamochabackend.substation.application.service;

import com.app.usochicamochabackend.actions.application.port.SaveActionUseCase;
import com.app.usochicamochabackend.auth.application.dto.UserPrincipal;
import com.app.usochicamochabackend.auth.infrastructure.entity.UserEntity;
import com.app.usochicamochabackend.auth.infrastructure.repository.UserRepositoryJpa;
import com.app.usochicamochabackend.exception.BadRequestException;
import com.app.usochicamochabackend.exception.ResourceNotFoundException;
import com.app.usochicamochabackend.substation.application.dto.*;
import com.app.usochicamochabackend.substation.application.port.SubstationCatalogUseCase;
import com.app.usochicamochabackend.substation.application.port.SubstationEjecucionUseCase;
import com.app.usochicamochabackend.substation.application.port.SubstationIndicadoresUseCase;
import com.app.usochicamochabackend.substation.infrastructure.entity.ActividadEntity;
import com.app.usochicamochabackend.substation.infrastructure.entity.DisciplinaEntity;
import com.app.usochicamochabackend.substation.infrastructure.entity.EjecucionEntity;
import com.app.usochicamochabackend.substation.infrastructure.entity.EvidenciaEntity;
import com.app.usochicamochabackend.substation.infrastructure.entity.ProgramacionEntity;
import com.app.usochicamochabackend.substation.infrastructure.repository.ActividadRepository;
import com.app.usochicamochabackend.substation.infrastructure.repository.CumplimientoViewRepository;
import com.app.usochicamochabackend.substation.infrastructure.repository.DisciplinaRepository;
import com.app.usochicamochabackend.substation.infrastructure.repository.EjecucionRepository;
import com.app.usochicamochabackend.substation.infrastructure.repository.EstacionRepository;
import com.app.usochicamochabackend.substation.infrastructure.repository.EvidenciaRepository;
import com.app.usochicamochabackend.substation.infrastructure.repository.IndicadorEstacionViewRepository;
import com.app.usochicamochabackend.substation.infrastructure.repository.ProgramacionRepository;
import com.app.usochicamochabackend.substation.infrastructure.repository.ResumenActividadViewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SubstationService implements SubstationCatalogUseCase, SubstationEjecucionUseCase, SubstationIndicadoresUseCase {

    private final EstacionRepository estacionRepository;
    private final ActividadRepository actividadRepository;
    private final DisciplinaRepository disciplinaRepository;
    private final ProgramacionRepository programacionRepository;
    private final EjecucionRepository ejecucionRepository;
    private final EvidenciaRepository evidenciaRepository;
    private final UserRepositoryJpa userRepositoryJpa;
    private final EvidenciaStorageService evidenciaStorageService;
    private final SaveActionUseCase saveActionUseCase;
    private final CumplimientoViewRepository cumplimientoViewRepository;
    private final IndicadorEstacionViewRepository indicadorEstacionViewRepository;
    private final ResumenActividadViewRepository resumenActividadViewRepository;

    @Override
    public List<EstacionResponse> listarEstaciones() {
        return estacionRepository.findByStatusTrueOrderByNombreAsc().stream()
                .map(EstacionResponse::fromEntity)
                .toList();
    }

    @Override
    public List<ActividadResponse> listarActividadesCapturables(String disciplina) {
        return actividadRepository
                .findByDisciplina_CodigoAndCapturaMovilHabilitadaTrueAndStatusTrueOrderByNombreAsc(disciplina).stream()
                .map(ActividadResponse::fromEntity)
                .toList();
    }

    @Override
    public List<ProgramacionResponse> listarProgramacion(Long estacionId, Integer anio, Integer mes, String disciplina) {
        return programacionRepository
                .findByEstacion_IdAndAnioAndMesAndActividad_Disciplina_CodigoAndStatusTrue(estacionId, anio, mes, disciplina)
                .stream()
                .map(ProgramacionResponse::fromEntity)
                .toList();
    }

    @Override
    @Transactional
    public EjecucionResponse registrarEjecucion(EjecucionRequest request, UserPrincipal usuario) {
        var existente = ejecucionRepository.findByUuidCliente(request.uuidCliente());
        if (existente.isPresent()) {
            return toResponse(existente.get());
        }

        validarCoherencia(request);

        var estacion = estacionRepository.findById(request.estacionId())
                .orElseThrow(() -> new ResourceNotFoundException("Estación no encontrada: id=" + request.estacionId()));

        DisciplinaEntity disciplina = disciplinaRepository.findByCodigo(request.disciplina())
                .orElseThrow(() -> new BadRequestException("Disciplina desconocida: " + request.disciplina()));

        ActividadEntity actividad = null;
        ProgramacionEntity programacion = null;
        if (request.actividadId() != null) {
            actividad = actividadRepository.findById(request.actividadId())
                    .orElseThrow(() -> new ResourceNotFoundException("Actividad no encontrada: id=" + request.actividadId()));
        }
        if (request.programacionId() != null) {
            programacion = programacionRepository.findById(request.programacionId())
                    .orElseThrow(() -> new ResourceNotFoundException("Cita de programación no encontrada: id=" + request.programacionId()));
        }

        UserEntity usuarioEntity = userRepositoryJpa.getUserEntityById(usuario.id());

        EjecucionEntity entity = EjecucionEntity.builder()
                .fecha(request.fecha())
                .mesEjecucion(request.mesEjecucion())
                .semanaEjecucion(request.semanaEjecucion())
                .usuario(usuarioEntity)
                .estacion(estacion)
                .disciplina(disciplina)
                .tipoMantenimiento(request.tipoMantenimiento())
                .tipoActividad(request.tipoActividad())
                .actividad(actividad)
                .programacion(programacion)
                .esProgramada(programacion != null)
                .motivoNoCatalogado(actividad == null ? request.motivoNoCatalogado() : null)
                .resultado(request.resultado())
                .observaciones(request.observaciones())
                .descripcionLibre(actividad == null ? request.descripcionLibre() : null)
                .uuidCliente(request.uuidCliente())
                .build();

        EjecucionEntity guardada = ejecucionRepository.save(entity);

        saveActionUseCase.save("El usuario " + usuarioEntity.getUsername()
                + " ha registrado una ejecución de mantenimiento en la estación " + estacion.getNombre());

        return toResponse(guardada);
    }

    private void validarCoherencia(EjecucionRequest request) {
        boolean tieneActividad = request.actividadId() != null;
        if (!tieneActividad) {
            if (request.motivoNoCatalogado() == null) {
                throw new BadRequestException("motivoNoCatalogado es obligatorio cuando actividadId es null.");
            }
            if (request.descripcionLibre() == null || request.descripcionLibre().isBlank()) {
                throw new BadRequestException("descripcionLibre es obligatoria cuando actividadId es null.");
            }
        }
    }

    @Override
    @Transactional
    public EvidenciaResponse agregarEvidencia(Long ejecucionId, MultipartFile file) throws IOException {
        EjecucionEntity ejecucion = ejecucionRepository.findById(ejecucionId)
                .orElseThrow(() -> new ResourceNotFoundException("Ejecución no encontrada: id=" + ejecucionId));

        var stored = evidenciaStorageService.store(file, ejecucionId);

        EvidenciaEntity entity = EvidenciaEntity.builder()
                .ejecucion(ejecucion)
                .rutaArchivo(stored.rutaRelativa())
                .nombreOriginal(stored.nombreOriginal())
                .build();

        return EvidenciaResponse.fromEntity(evidenciaRepository.save(entity));
    }

    @Override
    public EjecucionResponse obtenerEjecucion(Long id) {
        EjecucionEntity entity = ejecucionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ejecución no encontrada: id=" + id));
        return toResponse(entity);
    }

    @Override
    public Page<EjecucionResponse> listarEjecuciones(Long estacionId, LocalDate fechaInicio, LocalDate fechaFin, Pageable pageable) {
        LocalDate desde = fechaInicio != null ? fechaInicio : LocalDate.of(2000, 1, 1);
        LocalDate hasta = fechaFin != null ? fechaFin : LocalDate.now();
        return ejecucionRepository.findByEstacion_IdAndFechaBetween(estacionId, desde, hasta, pageable)
                .map(this::toResponse);
    }

    private EjecucionResponse toResponse(EjecucionEntity entity) {
        List<EvidenciaResponse> evidencias = evidenciaRepository.findByEjecucion_Id(entity.getId()).stream()
                .map(EvidenciaResponse::fromEntity)
                .toList();
        return EjecucionResponse.fromEntity(entity, evidencias);
    }

    @Override
    public List<CumplimientoResponse> cumplimientoPorMes(Integer anio, Integer mes, String disciplina) {
        return cumplimientoViewRepository.findByAnioAndMesAndDisciplinaOrderByEstacionNombreAsc(anio, mes, disciplina)
                .stream()
                .map(CumplimientoResponse::fromEntity)
                .toList();
    }

    @Override
    public List<CumplimientoResponse> cumplimientoPorEstacion(Long estacionId, Integer anio, String disciplina) {
        return cumplimientoViewRepository.findByEstacionIdAndAnioAndDisciplinaOrderByMesAsc(estacionId, anio, disciplina)
                .stream()
                .map(CumplimientoResponse::fromEntity)
                .toList();
    }

    @Override
    public List<IndicadorEstacionResponse> indicadoresPorEstacion() {
        return indicadorEstacionViewRepository.findAllByOrderByEstacionNombreAsc().stream()
                .map(IndicadorEstacionResponse::fromEntity)
                .toList();
    }

    @Override
    public List<ResumenActividadResponse> resumenPorActividad(String disciplina) {
        return resumenActividadViewRepository.findByDisciplinaOrderByActividadNombreAsc(disciplina).stream()
                .map(ResumenActividadResponse::fromEntity)
                .toList();
    }
}
