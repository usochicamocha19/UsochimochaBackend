package com.app.usochicamochabackend.substation.infrastructure.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/** Mapea la vista {@code v_mant_cumplimiento} (V37) — solo lectura, una fila por cita del cronograma. */
@Data
@Entity
@Immutable
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "v_mant_cumplimiento")
public class CumplimientoView {

    @Id
    @Column(name = "programacion_id")
    private Long programacionId;

    private Integer anio;

    private Integer mes;

    @Column(name = "estacion_id")
    private Long estacionId;

    @Column(name = "estacion_nombre")
    private String estacionNombre;

    @Column(name = "estacion_tipo")
    private String estacionTipo;

    @Column(name = "actividad_id")
    private Long actividadId;

    @Column(name = "actividad_nombre")
    private String actividadNombre;

    private String disciplina;

    private Integer ejecutado;

    private Boolean cumple;
}
