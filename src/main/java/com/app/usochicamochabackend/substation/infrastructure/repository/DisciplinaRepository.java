package com.app.usochicamochabackend.substation.infrastructure.repository;

import com.app.usochicamochabackend.substation.infrastructure.entity.DisciplinaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DisciplinaRepository extends JpaRepository<DisciplinaEntity, Long> {
    Optional<DisciplinaEntity> findByCodigo(String codigo);
}
