package com.app.usochicamochabackend.update.application.service;

import com.app.usochicamochabackend.actions.application.port.SaveActionUseCase;
import com.app.usochicamochabackend.auth.application.dto.UserPrincipal;
import com.app.usochicamochabackend.common.text.InputTextNormalizer;
import com.app.usochicamochabackend.exception.ResourceNotFoundException;
import com.app.usochicamochabackend.notifications.application.PreventiveAlertCalculationService;
import com.app.usochicamochabackend.update.application.dto.VehicleOilChangeHistoryDTO;
import com.app.usochicamochabackend.update.application.dto.VehicleOilChangeRequest;
import com.app.usochicamochabackend.update.application.exception.BrandNotFoundException;
import com.app.usochicamochabackend.update.application.exception.VehicleNotFoundException;
import com.app.usochicamochabackend.update.infrastructure.entity.BrandEntity;
import com.app.usochicamochabackend.update.infrastructure.entity.VehicleOilChangeEntity;
import com.app.usochicamochabackend.update.infrastructure.entity.OilType;
import com.app.usochicamochabackend.update.infrastructure.repository.BrandRepository;
import com.app.usochicamochabackend.update.infrastructure.repository.VehicleOilChangeRepository;
import com.app.usochicamochabackend.vehicle.infrastructure.entity.VehicleEntity;
import com.app.usochicamochabackend.vehicle.infrastructure.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class VehicleOilChangeService {

    private static final Logger logger = LoggerFactory.getLogger(VehicleOilChangeService.class);

    private final VehicleOilChangeRepository vehicleOilChangeRepository;
    private final VehicleRepository vehicleRepository;
    private final BrandRepository brandRepository;
    private final OilChangeValidationService validationService;
    private final PreventiveAlertCalculationService preventiveAlertCalculationService;
    private final SaveActionUseCase saveActionUseCase;

    @Transactional
    public void registerChange(VehicleOilChangeRequest request) {
        logger.info("Iniciando registro de cambio de aceite para vehículo: {}", request.placa());

        // Validar datos de entrada
        validationService.validateOilChangeRequest(request);

        try {
            String placa = InputTextNormalizer.normalizePlaca(request.placa());

            // Obtener vehículo
            VehicleEntity vehicle = vehicleRepository.findByPlaca(placa)
                    .orElseThrow(() -> {
                        logger.error("Vehículo no encontrado con placa: {}", placa);
                        return new VehicleNotFoundException(placa);
                    });

            logger.debug("Vehículo encontrado: {} (ID: {})", placa, vehicle.getIdVehiculo());

            // Obtener marca si se proporciona
            BrandEntity brand = null;
            if (request.brandId() != null) {
                brand = brandRepository.findById(request.brandId())
                        .orElseThrow(() -> {
                            logger.error("Marca no encontrada con ID: {}", request.brandId());
                            return new BrandNotFoundException(request.brandId());
                        });
                logger.debug("Marca encontrada: {}", brand.getName());
            }

            // Crear entidad de cambio de aceite
            VehicleOilChangeEntity entity = VehicleOilChangeEntity.builder()
                    .vehicle(vehicle)
                    .dateStamp(request.dateStamp() != null ? request.dateStamp() : java.time.LocalDateTime.now())
                    .oilType(OilType.fromString(request.oilType()))
                    .brand(brand)
                    .quantity(request.quantity())
                    .kmAtChange(request.kmAtChange())
                    .intervalKm(request.intervalKm())
                    .airFilterChanged(request.airFilterChanged())
                    .build();

            // Guardar cambio de aceite
            vehicleOilChangeRepository.save(entity);
            logger.debug("Cambio de aceite guardado: {} km, {} km intervalo",
                request.kmAtChange(), request.intervalKm());

            // Actualizar vehículo
            Integer currentKm = vehicle.getKilometrajeActual() != null ? vehicle.getKilometrajeActual() : 0;
            boolean needsUpdate = false;

            if (request.kmAtChange() != null && request.kmAtChange() > currentKm) {
                vehicle.setKilometrajeActual(request.kmAtChange());
                needsUpdate = true;
                logger.debug("Km actualizado: {} -> {}", currentKm, request.kmAtChange());
            }

            vehicle.setFechaUltimoReporte(java.time.LocalDateTime.now());
            needsUpdate = true;

            if (needsUpdate) {
                vehicleRepository.save(vehicle);
                logger.debug("Vehículo actualizado con km y fecha de reporte");
            }

            // Recalcular alertas de inmediato: este cambio de aceite resetea la línea base,
            // así que la alerta previa (si existía) debe desaparecer/actualizarse ya mismo.
            preventiveAlertCalculationService.calculateAndEmitAlerts();

            UserPrincipal userPrincipal = (UserPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            saveActionUseCase.save("El usuario " + userPrincipal.username() + " ha registrado un cambio de aceite para el vehículo " + placa);

            logger.info("Cambio de aceite registrado exitosamente para: {}", placa);

        } catch (VehicleNotFoundException | BrandNotFoundException e) {
            logger.error("Error de validación al registrar cambio de aceite: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("Error inesperado al registrar cambio de aceite para placa: {}",
                request.placa(), e);
            throw new RuntimeException("Error al registrar cambio de aceite: " + e.getMessage(), e);
        }
    }

    public List<VehicleOilChangeHistoryDTO> getHistoryByPlaca(String placa) {
        logger.debug("Obteniendo historial de cambios de aceite para: {}", placa);

        String p = InputTextNormalizer.normalizePlaca(placa);
        List<VehicleOilChangeEntity> history = vehicleOilChangeRepository.findAllByPlacaOrderByDateStampDesc(p);

        if (history.isEmpty()) {
            logger.warn("Sin historial de cambios de aceite para: {}", placa);
        } else {
            logger.debug("Se encontraron {} cambios de aceite para: {}", history.size(), placa);
        }

        return history.stream()
                .map(this::toHistoryDTO)
                .toList();
    }

    private VehicleOilChangeHistoryDTO toHistoryDTO(VehicleOilChangeEntity e) {
        return new VehicleOilChangeHistoryDTO(
                e.getId(),
                e.getDateStamp(),
                e.getOilType() != null ? e.getOilType().name() : null,
                e.getBrand() != null ? e.getBrand().getId() : null,
                e.getBrand() != null ? e.getBrand().getName() : null,
                e.getQuantity(),
                e.getKmAtChange(),
                e.getIntervalKm(),
                e.getAirFilterChanged()
        );
    }

    /**
     * Corrige un cambio de aceite ya registrado ("en caso de error") — mismo
     * patrón que RefuelingRecordService.actualizar(): busca por id+status activo,
     * actualiza los campos, y vuelve a aplicar el mismo efecto colateral que
     * registerChange() (km forward-only + recálculo de alertas), porque editar el
     * km de este registro puede requerir corregir kilometrajeActual también.
     */
    @Transactional
    public void actualizar(Long id, VehicleOilChangeRequest request) {
        validationService.validateOilChangeRequest(request);

        VehicleOilChangeEntity entity = vehicleOilChangeRepository.findByIdAndStatus(id, true)
                .orElseThrow(() -> new ResourceNotFoundException("Cambio de aceite no encontrado con id " + id));

        BrandEntity brand = null;
        if (request.brandId() != null) {
            brand = brandRepository.findById(request.brandId())
                    .orElseThrow(() -> new BrandNotFoundException(request.brandId()));
        }

        entity.setDateStamp(request.dateStamp() != null ? request.dateStamp() : entity.getDateStamp());
        entity.setOilType(OilType.fromString(request.oilType()));
        entity.setBrand(brand);
        entity.setQuantity(request.quantity());
        entity.setKmAtChange(request.kmAtChange());
        entity.setIntervalKm(request.intervalKm());
        entity.setAirFilterChanged(request.airFilterChanged());
        vehicleOilChangeRepository.save(entity);

        VehicleEntity vehicle = entity.getVehicle();
        Integer currentKm = vehicle.getKilometrajeActual() != null ? vehicle.getKilometrajeActual() : 0;
        if (request.kmAtChange() != null && request.kmAtChange() > currentKm) {
            vehicle.setKilometrajeActual(request.kmAtChange());
            vehicleRepository.save(vehicle);
        }

        preventiveAlertCalculationService.calculateAndEmitAlerts();

        UserPrincipal userPrincipal = (UserPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        saveActionUseCase.save("El usuario " + userPrincipal.username() + " ha corregido el cambio de aceite id=" + id + " del vehículo " + entity.getVehicle().getPlaca());

        logger.info("Cambio de aceite id={} actualizado", id);
    }

    /**
     * Soft-delete ("en caso de error") — no revierte kilometrajeActual aunque este
     * registro haya sido el que lo fijó: mismo criterio aceptado ya en
     * RefuelingRecordService.eliminar() para combustibles (limitación conocida).
     */
    @Transactional
    public void eliminar(Long id) {
        VehicleOilChangeEntity entity = vehicleOilChangeRepository.findByIdAndStatus(id, true)
                .orElseThrow(() -> new ResourceNotFoundException("Cambio de aceite no encontrado con id " + id));
        entity.setStatus(false);
        vehicleOilChangeRepository.save(entity);
        preventiveAlertCalculationService.calculateAndEmitAlerts();

        UserPrincipal userPrincipal = (UserPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        saveActionUseCase.save("El usuario " + userPrincipal.username() + " ha eliminado el cambio de aceite id=" + id + " del vehículo " + entity.getVehicle().getPlaca());

        logger.info("Cambio de aceite id={} eliminado (soft-delete)", id);
    }
}
