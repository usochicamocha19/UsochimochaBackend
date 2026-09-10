package com.app.usochicamochabackend.substation.application.dto;

import com.app.usochicamochabackend.substation.infrastructure.entity.EjecucionEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record EjecucionResponse(
        @Schema(description = "ID de la ejecución") Long id,
        LocalDate fecha,
        Integer mesEjecucion,
        Integer semanaEjecucion,
        Long estacionId,
        String estacionNombre,
        String disciplina,
        String tipoMantenimiento,
        String tipoActividad,
        Long actividadId,
        @Schema(description = "Nombre de la actividad, o null si fue no prevista/no catalogada") String actividadNombre,
        Boolean esProgramada,
        String motivoNoCatalogado,
        String resultado,
        String observaciones,
        String descripcionLibre,
        String responsable,
        UUID uuidCliente,
        List<EvidenciaResponse> evidencias,
        @Schema(description = "true si el resultado no es CONFORME y todavía no tiene ninguna evidencia adjunta")
        Boolean evidenciaPendiente,
        @Schema(description = "Historial de ediciones, orden cronológico") List<EjecucionEdicionResponse> ediciones
) {
    public static EjecucionResponse fromEntity(
            EjecucionEntity entity, List<EvidenciaResponse> evidencias, List<EjecucionEdicionResponse> ediciones) {
        boolean evidenciaPendiente = !"CONFORME".equals(entity.getResultado()) && evidencias.isEmpty();
        return new EjecucionResponse(
                entity.getId(),
                entity.getFecha(),
                entity.getMesEjecucion(),
                entity.getSemanaEjecucion(),
                entity.getEstacion().getId(),
                entity.getEstacion().getNombre(),
                entity.getDisciplina().getCodigo(),
                entity.getTipoMantenimiento(),
                entity.getTipoActividad(),
                entity.getActividad() != null ? entity.getActividad().getId() : null,
                entity.getActividad() != null ? entity.getActividad().getNombre() : null,
                entity.getEsProgramada(),
                entity.getMotivoNoCatalogado(),
                entity.getResultado(),
                entity.getObservaciones(),
                entity.getDescripcionLibre(),
                entity.getUsuario().getUsername(),
                entity.getUuidCliente(),
                evidencias,
                evidenciaPendiente,
                ediciones);
    }
}
