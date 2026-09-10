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

    Page<EjecucionEntity> findByProgramacion_Id(Long programacionId, Pageable pageable);

    Optional<EjecucionEntity> findFirstByProgramacion_IdOrderByIdDesc(Long programacionId);
}
