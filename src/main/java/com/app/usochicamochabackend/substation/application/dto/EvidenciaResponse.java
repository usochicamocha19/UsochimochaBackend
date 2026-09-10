package com.app.usochicamochabackend.substation.application.dto;

import com.app.usochicamochabackend.substation.infrastructure.entity.EvidenciaEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record EvidenciaResponse(
        @Schema(description = "ID de la evidencia", example = "1") Long id,
        @Schema(description = "Ruta relativa del archivo almacenado") String rutaArchivo,
        @Schema(description = "Nombre original del archivo subido") String nombreOriginal,
        @Schema(description = "Fecha/hora de subida") LocalDateTime subidoEn
) {
    public static EvidenciaResponse fromEntity(EvidenciaEntity entity) {
        return new EvidenciaResponse(entity.getId(), entity.getRutaArchivo(), entity.getNombreOriginal(), entity.getSubidoEn());
    }
}
