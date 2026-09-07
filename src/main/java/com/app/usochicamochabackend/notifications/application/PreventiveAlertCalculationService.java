package com.app.usochicamochabackend.notifications.application;

import com.app.usochicamochabackend.auth.infrastructure.repository.UserRepositoryJpa;
import com.app.usochicamochabackend.machine.infrastructure.entity.MachineEntity;
import com.app.usochicamochabackend.machine.infrastructure.repository.MachineRepository;
import com.app.usochicamochabackend.shared.calculator.DocumentAlertCalculator;
import com.app.usochicamochabackend.shared.calculator.OilChangeMachineAlertCalculator;
import com.app.usochicamochabackend.shared.calculator.OilChangeVehicleAlertCalculator;
import com.app.usochicamochabackend.update.infrastructure.entity.OilChangeEntity;
import com.app.usochicamochabackend.update.infrastructure.repository.OilChangeRepository;
import com.app.usochicamochabackend.update.infrastructure.repository.VehicleOilChangeRepository;
import com.app.usochicamochabackend.vehicle.infrastructure.entity.VehicleEntity;
import com.app.usochicamochabackend.vehicle.infrastructure.repository.VehicleRepository;
import com.app.usochicamochabackend.vehicleinspection.infrastructure.entity.DocumentacionYElementosEntity;
import com.app.usochicamochabackend.vehicleinspection.infrastructure.repository.DocumentacionYElementosRepository;
import com.app.usochicamochabackend.review.infrastructure.repository.InspectionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PreventiveAlertCalculationService {

    private final VehicleRepository vehicleRepository;
    private final MachineRepository machineRepository;
    private final DocumentacionYElementosRepository documentacionRepository;
    private final UserRepositoryJpa userRepository;
    private final VehicleOilChangeRepository vehicleOilChangeRepository;
    private final OilChangeRepository machineOilChangeRepository;
    private final InspectionRepository inspectionRepository;
    private final PreventiveAlertPersistenceService preventiveAlertPersistenceService;

    /**
     * FUNCIÓN ÚNICA: Calcula TODAS las alertas preventivas del sistema.
     * Se ejecuta por scheduler diariamente a las 07:00.
     *
     * Pasos:
     * 1. Limpiar alertas VERDES (no deben guardarse)
     * 2. Calcular alertas de DOCUMENTOS
     * 3. Calcular alertas de CAMBIO ACEITE (vehículos)
     * 4. Calcular alertas de CAMBIO ACEITE (maquinaria)
     * 5. Calcular alertas de LICENCIA (usuarios)
     */
    /**
     * Solo lectura: todas las escrituras de alertas se hacen en transacciones propias
     * (REQUIRES_NEW) vía {@link PreventiveAlertPersistenceService}, para que un registro
     * que falle al guardarse no revierta el lote completo calculado en este ciclo.
     */
    @Transactional(readOnly = true)
    public void calculateAndEmitAlerts() {

        try {
            cleanupGreenAlerts();
            calculateDocumentAlerts();
            calculateMachineDocumentAlerts();
            calculateOilChangeVehicleAlerts();
            calculateOilChangeMachineAlerts();
            calculateLicenseAlerts();
        } catch (Exception e) {
            log.error("❌ Error calculando alertas: {}", e.getMessage(), e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TIPO 1: DOCUMENTOS (SOAT, TECNOMECANICA, EXTINTOR para vehículos)
    // ─────────────────────────────────────────────────────────────────────────

    private void calculateDocumentAlerts() {
        var allDocs = documentacionRepository.findAll().stream()
            .filter(doc -> Boolean.TRUE.equals(doc.getActivo()))
            .filter(doc -> doc.getFechaVencimiento() != null)
            .toList();

        for (var doc : allDocs) {
            try {

                // Usar DocumentAlertCalculator
                var result = DocumentAlertCalculator.calculateDocumentAlert(
                    doc.getTipoDocumento(),      // "SOAT", "TECNOMECANICA", "EXTINTOR"
                    doc.getFechaVencimiento()
                );

                // Resolver el vehículo por ID en vez de usar doc.getVehiculo(): esa relación
                // lazy no queda poblada cuando el documento se acaba de guardar en la MISMA
                // transacción (el mapa de identidad de Hibernate reutiliza la instancia recién
                // construida por el builder, que nunca setea `vehiculo`), lo que guardaría la
                // alerta bajo el ID numérico del vehículo en vez de su placa.
                var vehiculoOpt = vehicleRepository.findById(doc.getIdVehiculo());

                // Obtener tipo de vehículo para assetType
                String assetType = "VEHICULO";
                if (vehiculoOpt.isPresent() && vehiculoOpt.get().getTipoVehiculo() != null) {
                    if ("MOTOCICLETA".equalsIgnoreCase(vehiculoOpt.get().getTipoVehiculo().getNombreTipo())) {
                        assetType = "MOTOCICLETA";
                    }
                }

                // Guardar o actualizar en BD (usar placa del vehículo, no ID)
                String assetId = vehiculoOpt.map(v -> v.getPlaca()).orElse(doc.getIdVehiculo().toString());

                // Si es VERDE (documento renovado/vigente), limpiar cualquier alerta previa de este subtipo
                if ("VERDE".equals(result.colorEstado)) {
                    preventiveAlertPersistenceService.deleteActiveAlertForAssetAndSubtype(
                        assetId, "DOCUMENTO", doc.getTipoDocumento()
                    );
                    continue;
                }

                preventiveAlertPersistenceService.saveOrUpdateAlert(
                    assetId,
                    assetType,
                    "DOCUMENTO",
                    doc.getTipoDocumento(),
                    result.colorEstado,
                    result.descripcion,
                    doc.getFechaVencimiento(),
                    "DIAS",
                    result.diasRestantes.doubleValue(),
                    null
                );
            } catch (Exception e) {
                log.error("❌ Error procesando documento {}: {}", doc.getIdDocumento(), e.getMessage(), e);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TIPO 1B: DOCUMENTOS DE MAQUINARIA (SOAT, SEGURO TODO RIESGO)
    // ─────────────────────────────────────────────────────────────────────────

    private void calculateMachineDocumentAlerts() {
        var machines = machineRepository.findAll().stream()
            .filter(m -> Boolean.TRUE.equals(m.getStatus()))
            .toList();

        for (var machine : machines) {
            try {
                calculateMachineDocumentAlert(machine, "SOAT", machine.getSoat());
                calculateMachineDocumentAlert(machine, "SEGURO TODO RIESGO", machine.getRunt());
            } catch (Exception e) {
                log.warn("⚠️ Error procesando documentos de máquina {}: {}", machine.getName(), e.getMessage());
            }
        }
    }

    private void calculateMachineDocumentAlert(MachineEntity machine, String tipoDocumento, LocalDate fechaVencimiento) {
        if (fechaVencimiento == null) {
            return;
        }

        var result = DocumentAlertCalculator.calculateDocumentAlert(tipoDocumento, fechaVencimiento);

        if ("VERDE".equals(result.colorEstado)) {
            preventiveAlertPersistenceService.deleteActiveAlertForAssetAndSubtype(
                machine.getName(), "DOCUMENTO", tipoDocumento
            );
            return;
        }

        preventiveAlertPersistenceService.saveOrUpdateAlert(
            machine.getName(),
            "MAQUINARIA",
            "DOCUMENTO",
            tipoDocumento,
            result.colorEstado,
            result.descripcion,
            fechaVencimiento,
            "DIAS",
            result.diasRestantes.doubleValue(),
            null
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TIPO 2: CAMBIO ACEITE VEHÍCULOS/MOTOS (por KILOMETRAJE)
    // ─────────────────────────────────────────────────────────────────────────

    private void calculateOilChangeVehicleAlerts() {

        var vehicles = vehicleRepository.findAll().stream()
            .filter(v -> Boolean.TRUE.equals(v.getActivo()))
            .toList();


        for (var vehicle : vehicles) {
            try {
                // Obtener último cambio de aceite usando VehicleOilChangeRepository
                var lastChange = vehicleOilChangeRepository
                    .findFirstByVehicleIdVehiculoAndStatusOrderByDateStampDesc(vehicle.getIdVehiculo(), true);

                String assetType = "VEHICULO";
                if (vehicle.getTipoVehiculo() != null &&
                    "MOTOCICLETA".equalsIgnoreCase(vehicle.getTipoVehiculo().getNombreTipo())) {
                    assetType = "MOTOCICLETA";
                }

                if (lastChange.isEmpty()) {
                    // Sin historial = alerta VERDE de "PRIMER CAMBIO" (informativo)
                    preventiveAlertPersistenceService.saveOrUpdateAlert(
                        vehicle.getPlaca(),
                        assetType,
                        "OIL_CHANGE_VEHICLE",
                        null,
                        "VERDE",
                        "PRIMER CAMBIO DE ACEITE RECOMENDADO",
                        null,
                        "KILOMETROS",
                        0.0,
                        100.0
                    );
                    continue;
                }

                var lastChangeRecord = lastChange.get();
                Integer kmActual = vehicle.getKilometrajeActual();
                Integer intervalKm = lastChangeRecord.getIntervalKm();

                if (kmActual == null || intervalKm == null || intervalKm <= 0) {
                    continue;
                }

                // Usar OilChangeVehicleAlertCalculator
                var result = OilChangeVehicleAlertCalculator.calculateOilChangeAlert(
                    kmActual,
                    lastChangeRecord.getKmAtChange(),
                    intervalKm
                );

                // Si es VERDE, no guardar
                if ("VERDE".equals(result.colorEstado)) {
                    preventiveAlertPersistenceService.deleteActiveAlertForAsset(
                        vehicle.getPlaca(),
                        "OIL_CHANGE_VEHICLE"
                    );
                    continue;
                }

                // Guardar AMARILLO o ROJO
                preventiveAlertPersistenceService.saveOrUpdateAlert(
                    vehicle.getPlaca(),
                    assetType,
                    "OIL_CHANGE_VEHICLE",
                    null,
                    result.colorEstado,
                    result.descripcion,
                    null,
                    "KILOMETROS",
                    result.kmRemaining.doubleValue(),
                    result.percentageUsed
                );
            } catch (Exception e) {
                log.warn("⚠️ Error procesando vehículo {}: {}", vehicle.getPlaca(), e.getMessage());
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TIPO 3: CAMBIO ACEITE MAQUINARIA (por HORÓMETRO)
    // ─────────────────────────────────────────────────────────────────────────

    private void calculateOilChangeMachineAlerts() {

        var machines = machineRepository.findAll().stream()
            .filter(m -> Boolean.TRUE.equals(m.getStatus()))
            .toList();

        for (var machine : machines) {
            try {
                // MOTOR OIL
                calculateMachineOilAlert(machine, true);
                // HYDRAULIC OIL
                calculateMachineOilAlert(machine, false);
            } catch (Exception e) {
                log.warn("⚠️ Error procesando máquina {}: {}", machine.getName(), e.getMessage());
            }
        }
    }

    private void calculateMachineOilAlert(MachineEntity machine, boolean isMotorOil) {
        String oilType = isMotorOil ? "MOTOR" : "HYDRAULIC";

        // IMPORTANTE: Usar el horómetro de la ÚLTIMA INSPECCIÓN
        var lastInspection = inspectionRepository.getLastInspection(machine.getId());
        Integer horometroActual = (lastInspection != null && lastInspection.getHourMeter() != null)
            ? lastInspection.getHourMeter().intValue()
            : (machine.getHorometroActual() != null ? machine.getHorometroActual() : 0);

        // Obtener último cambio usando OilChangeRepository
        var lastChange = isMotorOil
            ? machineOilChangeRepository.getLastMotorOilChangeByMachineId(machine.getId())
            : machineOilChangeRepository.getLastHydraulicOilChangeByMachineId(machine.getId());


        if (lastChange == null || lastChange.getHourStamp() == null) {
            // Sin historial = alerta VERDE de "PRIMER CAMBIO" (informativo)
            preventiveAlertPersistenceService.saveOrUpdateAlert(
                machine.getName(),
                "MAQUINARIA",
                "OIL_CHANGE_MACHINE",
                oilType,
                "VERDE",
                "ℹ️ PRIMER CAMBIO DE ACEITE " + oilType + " RECOMENDADO",
                null,
                "HORAS",
                0.0,
                100.0
            );
            return;
        }

        Integer hourStamp = lastChange.getHourStamp();
        Integer intervalHours = lastChange.getAverageHoursChange();

        if (intervalHours == null || intervalHours <= 0) {
            // Intervalo inválido = alerta VERDE (informativo)
            preventiveAlertPersistenceService.saveOrUpdateAlert(
                machine.getName(),
                "MAQUINARIA",
                "OIL_CHANGE_MACHINE",
                oilType,
                "VERDE",
                "ℹ️ PRIMER CAMBIO DE ACEITE " + oilType + " RECOMENDADO",
                null,
                "HORAS",
                0.0,
                100.0
            );
            return;
        }

        // Usar OilChangeMachineAlertCalculator
        var result = OilChangeMachineAlertCalculator.calculateOilChangeAlert(
            horometroActual,
            hourStamp,
            intervalHours,
            oilType
        );

        // Si es VERDE, no guardar (solo limpia el subtipo resuelto: MOTOR u HYDRAULIC, no ambos)
        if ("VERDE".equals(result.colorEstado)) {
            preventiveAlertPersistenceService.deleteActiveAlertForAssetAndSubtype(
                machine.getName(),
                "OIL_CHANGE_MACHINE",
                oilType
            );
            return;
        }

        // Guardar AMARILLO o ROJO
        preventiveAlertPersistenceService.saveOrUpdateAlert(
            machine.getName(),
            "MAQUINARIA",
            "OIL_CHANGE_MACHINE",
            oilType,
            result.colorEstado,
            result.descripcion,
            null,
            "HORAS",
            result.horasRemaining.doubleValue(),
            result.percentageUsed
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // BONUS: LICENCIA DE CONDUCCIÓN (usuarios)
    // ─────────────────────────────────────────────────────────────────────────

    private void calculateLicenseAlerts() {

        // Nota: no se acota por rango de fechas (ej. "próximos 45 días") para que el
        // calculador decida el color en TODOS los casos, incluida licencia ya VENCIDA
        // (fecha en el pasado) y licencia recién renovada muy a futuro (VERDE → limpia
        // la alerta previa). Acotar aquí excluía esos casos y dejaba alertas huérfanas.
        var usersWithExpiry = userRepository.findAll().stream()
            .filter(u -> Boolean.TRUE.equals(u.getStatus()))
            .filter(u -> u.getLicenseExpiry() != null)
            .toList();


        for (var user : usersWithExpiry) {
            try {
                var result = DocumentAlertCalculator.calculateDocumentAlert(
                    "LICENCIA",
                    user.getLicenseExpiry()
                );

                if ("VERDE".equals(result.colorEstado)) {
                    preventiveAlertPersistenceService.deleteActiveAlertForAssetAndSubtype(
                        user.getUsername(), "DOCUMENTO", "LICENCIA"
                    );
                    continue;
                }

                preventiveAlertPersistenceService.saveOrUpdateAlert(
                    user.getUsername(),
                    "USUARIO",
                    "DOCUMENTO",
                    "LICENCIA",
                    result.colorEstado,
                    "🪪 " + result.descripcion,
                    user.getLicenseExpiry(),
                    "DIAS",
                    result.diasRestantes.doubleValue(),
                    null
                );
            } catch (Exception e) {
                log.warn("⚠️ Error procesando licencia de usuario {}: {}", user.getUsername(), e.getMessage());
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    private void cleanupGreenAlerts() {
        preventiveAlertPersistenceService.cleanupGreenAlerts();
    }
}
