package com.app.usochicamochabackend.substation.infrastructure.entity;

import com.app.usochicamochabackend.auth.infrastructure.entity.UserEntity;
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
@Table(name = "mant_ejecucion_edicion")
public class EjecucionEdicionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "ejecucion_id", nullable = false)
    private EjecucionEntity ejecucion;

    @ManyToOne
    @JoinColumn(name = "usuario_id", nullable = false)
    private UserEntity usuario;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String motivo;

    @Column(name = "editado_en", nullable = false)
    @Builder.Default
    private LocalDateTime editadoEn = LocalDateTime.now();
}
