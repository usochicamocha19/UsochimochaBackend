package com.app.usochicamochabackend.substation.infrastructure.repository;

import com.app.usochicamochabackend.substation.infrastructure.entity.EjecucionEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface EjecucionRepository extends JpaRepository<EjecucionEntity, Long> {

    boolean existsByUuidCliente(UUID uuidCliente);

    Optional<EjecucionEntity> findByUuidCliente(UUID uuidCliente);

    Page<EjecucionEntity> findByEstacion_IdAndFechaBetween(Long estacionId, LocalDate desde, LocalDate hasta, Pageable pageable);

    Page<EjecucionEntity> findByEstacion_IdAndFechaBetweenAndEsProgramada(
            Long estacionId, LocalDate desde, LocalDate hasta, Boolean esProgramada, Pageable pageable);

    /** Sin filtro de estación: todas las estaciones para el rango de fechas dado. */
    Page<EjecucionEntity> findByFechaBetween(LocalDate desde, LocalDate hasta, Pageable pageable);

    /** Sin filtro de estación, con filtro de esProgramada (ej. solo actividades no previstas). */
    Page<EjecucionEntity> findByFechaBetweenAndEsProgramada(
            LocalDate desde, LocalDate hasta, Boolean esProgramada, Pageable pageable);

    Page<EjecucionEntity> findByProgramacion_Id(Long programacionId, Pageable pageable);

    Optional<EjecucionEntity> findFirstByProgramacion_IdOrderByIdDesc(Long programacionId);
}
