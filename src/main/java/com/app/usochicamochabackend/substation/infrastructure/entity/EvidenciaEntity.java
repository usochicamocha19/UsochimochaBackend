package com.app.usochicamochabackend.substation.infrastructure.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "mant_evidencia")
public class EvidenciaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "ejecucion_id", nullable = false)
    private EjecucionEntity ejecucion;

    @Column(name = "ruta_archivo", nullable = false)
    private String rutaArchivo;

    @Column(name = "nombre_original", nullable = false)
    private String nombreOriginal;

    @Column(name = "subido_en", nullable = false)
    @Builder.Default
    private LocalDateTime subidoEn = LocalDateTime.now();
}
