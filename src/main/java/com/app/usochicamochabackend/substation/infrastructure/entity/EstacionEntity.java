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
@Table(name = "mant_estacion")
public class EstacionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String nombre;

    @Column(nullable = false)
    private String tipo; // BOMBEO / COMPLEMENTARIA (CHECK en BD)

    @Column(name = "frecuencia_base", nullable = false)
    private String frecuenciaBase; // TRIMESTRAL / ANUAL (CHECK en BD)

    @Builder.Default
    private Boolean status = true;
}
