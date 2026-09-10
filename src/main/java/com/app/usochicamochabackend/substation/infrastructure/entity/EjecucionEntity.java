package com.app.usochicamochabackend.substation.infrastructure.entity;

import com.app.usochicamochabackend.auth.infrastructure.entity.UserEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "mant_ejecucion")
public class EjecucionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(name = "mes_ejecucion", nullable = false)
    private Integer mesEjecucion;

    @Column(name = "semana_ejecucion", nullable = false)
    private Integer semanaEjecucion;

    @ManyToOne
    @JoinColumn(name = "usuario_id", nullable = false)
    private UserEntity usuario;

    @ManyToOne
    @JoinColumn(name = "estacion_id", nullable = false)
    private EstacionEntity estacion;

    @ManyToOne
    @JoinColumn(name = "disciplina_id", nullable = false)
    private DisciplinaEntity disciplina;

    @Column(name = "tipo_mantenimiento", nullable = false)
    private String tipoMantenimiento; // PREVENTIVO / CORRECTIVO / PREDICTIVO / NO_PROGRAMADO

    @Column(name = "tipo_actividad", nullable = false)
    private String tipoActividad; // INSPECCION / MANTENIMIENTO / NO_PROGRAMADO / OTRO

    @ManyToOne
    @JoinColumn(name = "actividad_id")
    private ActividadEntity actividad; // NULL si no vino de una cita del cronograma

    @ManyToOne
    @JoinColumn(name = "programacion_id")
    private ProgramacionEntity programacion; // NULL si no vino de una cita del cronograma

    @Column(name = "es_programada", nullable = false)
    private Boolean esProgramada; // derivado de la ruta tomada por el técnico, no un dropdown

    @Column(name = "motivo_no_catalogado")
    private String motivoNoCatalogado; // NO_PROGRAMADO / OTRO — NULL cuando actividad IS NOT NULL

    @Column(nullable = false)
    private String resultado; // CONFORME / CON_HALLAZGOS / REQUIERE_INTERVENCION

    @Column(nullable = false, columnDefinition = "TEXT")
    private String observaciones;

    @Column(name = "descripcion_libre", columnDefinition = "TEXT")
    private String descripcionLibre; // requerido cuando actividad IS NULL

    @Column(name = "uuid_cliente", nullable = false, unique = true)
    private UUID uuidCliente; // idempotencia offline (móvil)
}
