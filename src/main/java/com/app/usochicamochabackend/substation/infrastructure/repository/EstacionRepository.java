package com.app.usochicamochabackend.substation.infrastructure.repository;

import com.app.usochicamochabackend.substation.infrastructure.entity.EstacionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EstacionRepository extends JpaRepository<EstacionEntity, Long> {
    List<EstacionEntity> findByStatusTrueOrderByNombreAsc();
}
