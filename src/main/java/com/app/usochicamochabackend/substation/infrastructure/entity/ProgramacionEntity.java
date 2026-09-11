package com.app.usochicamochabackend.substation.infrastructure.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "mant_programacion")
public class ProgramacionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Integer anio;

    @Column(nullable = false)
    private Integer mes;

    @ManyToOne
    @JoinColumn(name = "estacion_id", nullable = false)
    private EstacionEntity estacion;

    @ManyToOne
    @JoinColumn(name = "actividad_id", nullable = false)
    private ActividadEntity actividad;

    @Builder.Default
    private Boolean status = true;
}
