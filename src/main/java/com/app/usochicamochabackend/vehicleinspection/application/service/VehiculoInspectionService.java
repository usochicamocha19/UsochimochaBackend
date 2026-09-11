package com.app.usochicamochabackend.vehicleinspection.application.service;

import com.app.usochicamochabackend.actions.application.port.SaveActionUseCase;
import com.app.usochicamochabackend.auth.application.dto.UserPrincipal;
import com.app.usochicamochabackend.catalog.infrastructure.repository.UbicacionRepository;
import com.app.usochicamochabackend.common.text.InputTextNormalizer;
import com.app.usochicamochabackend.vehicle.infrastructure.entity.VehicleEntity;
import com.app.usochicamochabackend.vehicle.infrastructure.repository.VehicleRepository;
import com.app.usochicamochabackend.vehicleinspection.application.dto.*;
import com.app.usochicamochabackend.notifications.application.NotificationService;
import com.app.usochicamochabackend.notifications.application.PreventiveAlertCalculationService;
import com.app.usochicamochabackend.vehicleinspection.application.port.CreateVehiculoInspectionUseCase;
import com.app.usochicamochabackend.vehicleinspection.application.port.GetVehicleInspectionsUseCase;
import com.app.usochicamochabackend.vehicleinspection.infrastructure.entity.*;
import com.app.usochicamochabackend.vehicleinspection.infrastructure.repository.DocumentacionYElementosRepository;
import com.app.usochicamochabackend.vehicleinspection.infrastructure.repository.InspDetalleDocumentosRepository;
import com.app.usochicamochabackend.vehicleinspection.infrastructure.repository.InspDetalleElementosRepository;
import com.app.usochicamochabackend.vehicleinspection.infrastructure.repository.InspDetalleMecanicoRepository;
import com.app.usochicamochabackend.vehicleinspection.infrastructure.repository.InspDetalleSaludRepository;
import com.app.usochicamochabackend.vehicleinspection.infrastructure.repository.InspPreOperativaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.YearMonth;
import java.io.IOException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
public class VehiculoInspectionService implements CreateVehiculoInspectionUseCase, GetVehicleInspectionsUseCase {

    private final InspPreOperativaRepository inspPreOperativaRepository;
    private final InspDetalleMecanicoRepository detalleMecanicoRepository;
    @Qualifier("vehicleInspDetalleDocumentosRepository")
    private final InspDetalleDocumentosRepository detalleDocumentosRepository;
    private final InspDetalleElementosRepository detalleElementosRepository;
    private final InspDetalleSaludRepository detalleSaludRepository;
    private final DocumentacionYElementosRepository documentacionRepository;
    private final UbicacionRepository ubicacionRepository;
    private final VehicleRepository vehicleRepository;
    private final VehicleDocumentStorageService vehicleDocumentStorageService;
    private final NotificationService notificationService;
    private final PreventiveAlertCalculationService preventiveAlertCalculationService;
    private final SaveActionUseCase saveActionUseCase;

    /**
     * POST — Guarda la inspección pre-operativa en las 5 tablas de inspección
     * y, si el cliente envía fechas/URLs, fusiona en {@code documentacion_y_elementos}.
     */
    @Override
    @Transactional
    public VehiculoInspectionResponse create(VehiculoInspectionRequest req, UserPrincipal inspector) {

        // ── Resolver idVehiculo a partir de la placa ─────────────────────────
        String placaNorm = InputTextNormalizer.normalizePlaca(req.placaVehiculo());
        VehicleEntity vehicle = vehicleRepository.findByPlaca(placaNorm)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Vehículo no encontrado con placa: " + placaNorm));

        Integer idVehiculo = vehicle.getIdVehiculo();

        // ── 1: Cabecera — inspeccion_pre_operativa ────────────────────────────
        InspPreOperativaEntity cabecera = InspPreOperativaEntity.builder()
                .fechaRegistro(LocalDateTime.now())
                .idVehiculo(idVehiculo)
                .loginUser(inspector.username()) // username del usuario autenticado
                .kilometrajeReportado(req.kilometrajeReportado() != null ? req.kilometrajeReportado() : 0)
                .aprobadoRuta(req.aprobadoRuta())
                .observacionesFinales(req.observacionesFinales())
                .idUbicacion(resolveIdUbicacionForNewInspection(req, vehicle))
                .build();

        InspPreOperativaEntity saved = inspPreOperativaRepository.save(cabecera);
        Long idInspeccion = saved.getIdInspeccion();

        // ── 2: insp_detalle_mecanico ──────────────────────────────────────────
        detalleMecanicoRepository.save(
                InspDetalleMecanicoEntity.builder()
                        .idInspeccion(idInspeccion)
                        .nivelAceite(req.nivelAceite())
                        .nivelRefrigerante(req.nivelRefrigerante())
                        .nivelFrenos(req.nivelFrenos())
                        .estadoLlantas(req.estadoLlantas())
                        .lucesGeneral(req.lucesGeneral())
                        .estadoVisual(req.estadoVisual())
                        .limpiezaGeneral(req.limpiezaGeneral())
                        .build());

        // ── 3: insp_detalle_documentos ────────────────────────────────────────
        // Check visual del inspector (Vigente / Próximo a Vencer / Vencido)
        // Nota: checkLicencia viene en el DTO pero no se guarda en BD (solo SOAT, TECNO, EXTINTOR)
        detalleDocumentosRepository.save(
                InspDetalleDocumentosEntity.builder()
                        .idInspeccion(idInspeccion)
                        .checkSoat(req.checkSoat())
                        .checkTecno(req.checkTecno())
                        .checkExtintor(req.checkExtintor())
                        .build());

        // ── 4: insp_detalle_elementos ─────────────────────────────────────────
        detalleElementosRepository.save(
                InspDetalleElementosEntity.builder()
                        .idInspeccion(idInspeccion)
                        .tieneBotiquin(req.tieneBotiquin())
                        .tieneSeñalizacion(req.tieneSeñalizacion())
                        .tieneLineasEmergencia(req.tieneLineasEmergencia()) // → tiene_extintor col.
                        .tieneLlantaRepuesto(booleanToSiNo(req.tieneLlantaRepuesto()))
                        .tieneGatoHidraulico(booleanToSiNo(req.tieneGatoHidraulico()))
                        .build());

        // ── 5: insp_detalle_salud ─────────────────────────────────────────────
        detalleSaludRepository.save(
                InspDetalleSaludEntity.builder()
                        .idInspeccion(idInspeccion)
                        .saludFisica(req.saludFisica())
                        .saludMental(req.saludMental())
                        .sobrio(req.sobrio())
                        .medicamentos(req.medicamentos())
                        .condicionParaConducir(req.condicionParaConducir())
                        .conscienteResponsabilidad(req.conscienteResponsabilidad())
                        .build());

        mergeDocumentsFromInspectionRequest(idVehiculo, req, inspector);

        // ── Actualizar kilometraje y fecha de último reporte (monitoreo) ───────
        if (req.kilometrajeReportado() != null && req.kilometrajeReportado() > 0) {
            vehicleRepository.updateKilometrajeWithDate(
                    idVehiculo,
                    req.kilometrajeReportado(),
                    LocalDateTime.now());
            vehicleRepository.flush(); // Sincronizar cambios con BD antes de calcular alertas
        } else {
            // Si no hay kilometraje, actualizar solo la fecha del último reporte
            vehicle.setFechaUltimoReporte(LocalDateTime.now());
            vehicleRepository.save(vehicle);
            vehicleRepository.flush();
        }

        // ── Determinar evento de notificación dentro de la transacción ────────
        // Acceder a la relación lazy ANTES de que la transacción termine
        String tipoNombre = "";
        if (vehicle.getTipoVehiculo() != null && vehicle.getTipoVehiculo().getNombreTipo() != null) {
            tipoNombre = vehicle.getTipoVehiculo().getNombreTipo().toUpperCase(Locale.ROOT);
        }
        String updateEvent = tipoNombre.contains("MOTO") ? "moto-inspections-updated" : "vehicle-inspections-updated";

        // Recalcular alertas de inmediato: el kilometraje reportado pudo haber cruzado
        // el umbral de cambio de aceite.
        preventiveAlertCalculationService.calculateAndEmitAlerts();
        notificationService.notifyDataUpdate(updateEvent);

        saveActionUseCase.save("El usuario " + inspector.username() + " ha registrado una inspección para el vehículo " + placaNorm);

        return new VehiculoInspectionResponse(idInspeccion, "Inspección guardada exitosamente");
    }

    /**
     * Persiste fechas/URLs opcionales en documentacion_y_elementos (misma semántica que
     * {@link #saveDocument(VehicleDocumentRequest)} / GET documentos).
     */
    /**
     * La inspección solo registra la fecha de vencimiento — nunca reemplaza la URL del archivo.
     * La app envía URLs absolutas (resueltas por resolveUrl) que no coinciden con la ruta
     * relativa almacenada en BD, lo que causaría un nuevo registro por cada inspección.
     * Pasar null como URL hace que saveDocument use la URL ya almacenada en BD para la
     * comparación y el guardado, evitando duplicados cuando la fecha no cambia.
     */
    private void mergeDocumentsFromInspectionRequest(Integer idVehiculo, VehiculoInspectionRequest req, UserPrincipal inspector) {
        String u = inspector.username();
        upsertDocumentIfPresent(idVehiculo, "SOAT", req.fechaVencSoat(), null, u);
        upsertDocumentIfPresent(idVehiculo, "TECNOMECANICA", req.fechaVencTecno(), null, u);
        upsertDocumentIfPresent(idVehiculo, "LICENCIA", req.fechaVencLicencia(), null, u);

        LocalDate extintorFecha = parseExtintorMonth(req.vigenciaExtintor());
        if (extintorFecha != null) {
            saveDocument(new VehicleDocumentRequest(
                    idVehiculo,
                    "EXTINTOR",
                    extintorFecha,
                    null,
                    null), u);
        }
    }

    private void upsertDocumentIfPresent(Integer idVehiculo, String tipoBd, String fechaRaw, String imagenUrl, String registradoPor) {
        LocalDate fecha = parseFlexibleDate(fechaRaw);
        if (fecha == null) {
            return;
        }
        saveDocument(new VehicleDocumentRequest(idVehiculo, tipoBd, fecha, imagenUrl, null), registradoPor);
    }

    private static String blankToNull(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return s.trim();
    }

    /**
     * Acepta {@code yyyy-MM-dd} o el prefijo de un instante ISO (toma los primeros 10 caracteres).
     */
    private static LocalDate parseFlexibleDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String s = raw.trim();
        if (s.length() >= 10) {
            s = s.substring(0, 10);
        }
        try {
            return LocalDate.parse(s, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** Vigencia extintor en {@code yyyy-MM} → último día del mes. */
    private static LocalDate parseExtintorMonth(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String s = raw.trim();
        if (s.length() >= 7) {
            s = s.substring(0, 7);
        }
        try {
            return YearMonth.parse(s, DateTimeFormatter.ofPattern("yyyy-MM")).atEndOfMonth();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** Solo filas cuyo vehículo/moto siga activo en inventario (`vehiculos.activo`; excluye dados de baja del dashboard). */
    private static boolean isActiveVehicleForDashboard(InspPreOperativaEntity row) {
        VehicleEntity v = row.getVehiculo();
        if (v == null) {
            return false;
        }
        return !Boolean.FALSE.equals(v.getActivo());
    }

    @Override
    public List<VehicleInspectionReportDTO> getInspectionsByType(Integer typeId) {
        List<InspPreOperativaEntity> inspections = inspPreOperativaRepository.findAllByVehicleType(typeId).stream()
                .filter(VehiculoInspectionService::isActiveVehicleForDashboard)
                .sorted(Comparator.comparing(InspPreOperativaEntity::getFechaRegistro).reversed())
                .toList();
        return mapToReportDTO(inspections);
    }

    private static String dedupeKeyVehicleReport(InspPreOperativaEntity row) {
        if (row.getVehiculo() != null && row.getVehiculo().getPlaca() != null) {
            String p = row.getVehiculo().getPlaca().trim();
            if (!p.isEmpty()) return "p:" + p.toUpperCase(Locale.ROOT);
        }
        return "v:" + (row.getIdVehiculo() != null ? row.getIdVehiculo() : 0);
    }

    @Override
    public List<VehicleInspectionReportDTO> getAllVehicleInspections() {
        return mapToReportDTO(inspPreOperativaRepository.findAllNonMoto().stream()
                .filter(VehiculoInspectionService::isActiveVehicleForDashboard)
                .toList());
    }

    @Override
    public List<VehicleInspectionReportDTO> getLatestVehicleInspectionsPerVehicle() {
        List<InspPreOperativaEntity> ordered = inspPreOperativaRepository.findAllNonMoto().stream()
                .filter(VehiculoInspectionService::isActiveVehicleForDashboard)
                .sorted(Comparator.comparing(InspPreOperativaEntity::getFechaRegistro).reversed())
                .toList();
        return mapToReportDTO(ordered);
    }

    private List<InspPreOperativaEntity> listMotoInspectionEntitiesNewestFirst() {
        return inspPreOperativaRepository.findAllByVehicleTypeName("MOTOCICLETA").stream()
                .filter(VehiculoInspectionService::isActiveVehicleForDashboard)
                .toList();
    }

    @Override
    public List<VehicleInspectionReportDTO> getMotoInspectionsHistory() {
        return mapToReportDTO(listMotoInspectionEntitiesNewestFirst());
    }

    @Override
    public List<VehicleInspectionReportDTO> getMotoInspectionsLatestPerVehicle() {
        List<InspPreOperativaEntity> ordered = listMotoInspectionEntitiesNewestFirst();
        return mapToReportDTO(ordered);
    }

    private static String dedupeKeyMotoReport(InspPreOperativaEntity row) {
        if (row.getVehiculo() != null && row.getVehiculo().getPlaca() != null) {
            String p = row.getVehiculo().getPlaca().trim();
            if (!p.isEmpty()) {
                return "p:" + p.toUpperCase(Locale.ROOT);
            }
        }
        return "v:" + (row.getIdVehiculo() != null ? row.getIdVehiculo() : 0);
    }

    /**
     * Texto de ubicación para reportes: primero la de la inspección ({@code id_ubicacion});
     * si no hay, la ubicación base del vehículo en inventario ({@code id_ubicacion_base}).
     */
    private String resolveUbicacionNombre(InspPreOperativaEntity inspection, VehicleEntity vehicle) {
        if (inspection.getIdUbicacion() != null) {
            String fromInsp = ubicacionRepository.findById(inspection.getIdUbicacion())
                    .map(u -> u.getNombreUbicacion())
                    .filter(n -> n != null && !n.isBlank())
                    .orElse(null);
            if (fromInsp != null) {
                return fromInsp;
            }
        }
        if (vehicle != null && vehicle.getUbicacionBase() != null) {
            String n = vehicle.getUbicacionBase().getNombreUbicacion();
            if (n != null && !n.isBlank()) {
                return n;
            }
        }
        return "N/A";
    }

    /** Si el cliente no envía ubicación, se toma la base del vehículo para persistir y reportes. */
    private static Integer resolveIdUbicacionForNewInspection(VehiculoInspectionRequest req, VehicleEntity vehicle) {
        if (req.idUbicacion() != null) {
            return req.idUbicacion();
        }
        if (vehicle != null && vehicle.getUbicacionBase() != null) {
            return vehicle.getUbicacionBase().getId();
        }
        return null;
    }

    private List<VehicleInspectionReportDTO> mapToReportDTO(List<InspPreOperativaEntity> inspections) {
        List<VehicleInspectionReportDTO> reportList = new java.util.ArrayList<>();

        for (InspPreOperativaEntity inspection : inspections) {
            Long id = inspection.getIdInspeccion();
            var mecanico = detalleMecanicoRepository.findByIdInspeccion(id).orElse(new InspDetalleMecanicoEntity());
            var documentos = detalleDocumentosRepository.findByIdInspeccion(id).orElse(new InspDetalleDocumentosEntity());
            var elementos = detalleElementosRepository.findByIdInspeccion(id).orElse(new InspDetalleElementosEntity());
            var salud = detalleSaludRepository.findByIdInspeccion(id).orElse(new InspDetalleSaludEntity());
            var vehicle = inspection.getVehiculo();

            reportList.add(new VehicleInspectionReportDTO(
                id,
                inspection.getFechaRegistro(),
                (vehicle != null) ? vehicle.getPlaca() : "N/A",
                (vehicle != null && vehicle.getMarca() != null) ? vehicle.getMarca().getDescripcion() : null,
                (vehicle != null && vehicle.getTipoVehiculo() != null) ? vehicle.getTipoVehiculo().getNombreTipo() : null,
                (vehicle != null && vehicle.getBelongsTo() != null && !vehicle.getBelongsTo().isBlank())
                        ? vehicle.getBelongsTo()
                        : "N/A",
                resolveUbicacionNombre(inspection, vehicle),
                inspection.getLoginUser(),
                inspection.getKilometrajeReportado(),
                inspection.getAprobadoRuta(),
                inspection.getObservacionesFinales(),
                
                mecanico.getNivelAceite(),
                mecanico.getNivelRefrigerante(),
                mecanico.getNivelFrenos(),
                mecanico.getEstadoLlantas(),
                mecanico.getLucesGeneral(),
                mecanico.getEstadoVisual(),
                mecanico.getLimpiezaGeneral(),
                
                documentos.getCheckSoat(),
                documentos.getCheckTecno(),
                documentos.getCheckExtintor(),
                
                elementos.getTieneBotiquin(),
                elementos.getTieneSeñalizacion(),
                elementos.getTieneLineasEmergencia(),
                elementos.getTieneLlantaRepuesto(),
                elementos.getTieneGatoHidraulico(),
                
                salud.getSaludFisica(),
                salud.getSaludMental(),
                salud.getSobrio(),
                salud.getMedicamentos(),
                salud.getCondicionParaConducir(),
                salud.getConscienteResponsabilidad(),

                null,  // mechanicName - no disponible en inspección
                null,  // contractorName - no disponible en inspección
                null   // timeSpent - no disponible en inspección
            ));
        }
        return reportList;
    }

    /**
     * GET — Valida si el kilometraje ingresado por el inspector es menor
     * al kilometraje_actual registrado para el vehículo.
     *
     * @param placa                Placa del vehículo a consultar.
     * @param kilometrajeIngresado Valor que el inspector está por registrar.
     * @return {@link KilometrajeValidacionResponse} con {@code alerta=true} si el
     *         valor ingresado es menor al registrado, {@code false} en caso
     *         contrario.
     */
    public KilometrajeValidacionResponse validarKilometraje(String placa, Integer kilometrajeIngresado) {

        String p = InputTextNormalizer.normalizePlaca(placa);
        VehicleEntity vehicle = vehicleRepository.findByPlaca(p)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Vehículo no encontrado con placa: " + p));

        Integer kilometrajeActual = vehicle.getKilometrajeActual();

        // Si el vehículo no tiene kilometraje registrado aún, no hay alerta
        if (kilometrajeActual == null || kilometrajeActual == 0) {
            return new KilometrajeValidacionResponse(false, "Sin kilometraje previo registrado.");
        }

        if (kilometrajeIngresado < kilometrajeActual) {
            return new KilometrajeValidacionResponse(
                    true,
                    "El kilometraje ingresado (" + kilometrajeIngresado + " km) es menor al registrado ("
                            + kilometrajeActual + " km).");
        }

        return new KilometrajeValidacionResponse(false, "Kilometraje correcto.");
    }

    /**
     * GET — Lee documentacion_y_elementos (tabla administrada por la web/admin)
     * y retorna las fechas de vencimiento con su estado calculado en tiempo real.
     *
     * Estados: "Vigente" | "Próximo a Vencer" (≤ 30 días) | "Vencido"
     */
    public DocumentoVehiculoResponse getDocumentos(Integer idVehiculo) {

        String fechaSoat = null, estadoSoat = null, urlSoat = null;
        String fechaTecno = null, estadoTecno = null, urlTecno = null;
        String urlTarjetaPropiedad = null;
        String fechaExtintor = null, estadoExtintor = null, urlExtintor = null;

        var soat = documentacionRepository.findLatestByVehiculoAndTipo(idVehiculo, "SOAT");
        if (soat.isPresent()) {
            fechaSoat = soat.get().getFechaVencimiento() != null ? soat.get().getFechaVencimiento().toString() : null;
            estadoSoat = calcularEstado(soat.get().getFechaVencimiento());
            urlSoat = resolveUrl(soat.get().getImagenUrl());
        }

        var tecno = documentacionRepository.findLatestByVehiculoAndTipo(idVehiculo, "TECNOMECANICA");
        if (tecno.isPresent()) {
            fechaTecno = tecno.get().getFechaVencimiento() != null ? tecno.get().getFechaVencimiento().toString() : null;
            estadoTecno = calcularEstado(tecno.get().getFechaVencimiento());
            urlTecno = resolveUrl(tecno.get().getImagenUrl());
        }

        var tarjeta = documentacionRepository.findLatestByVehiculoAndTipo(idVehiculo, "TARJETA DE PROPIEDAD");
        if (tarjeta.isPresent()) {
            urlTarjetaPropiedad = resolveUrl(tarjeta.get().getImagenUrl());
        }

        var extintor = documentacionRepository.findLatestByVehiculoAndTipo(idVehiculo, "EXTINTOR");
        if (extintor.isPresent()) {
            fechaExtintor = extintor.get().getFechaVencimiento() != null ? extintor.get().getFechaVencimiento().toString() : null;
            estadoExtintor = calcularEstado(extintor.get().getFechaVencimiento());
            urlExtintor = resolveUrl(extintor.get().getImagenUrl());
        }

        return new DocumentoVehiculoResponse(
                idVehiculo,
                fechaSoat, estadoSoat, urlSoat,
                fechaTecno, estadoTecno, urlTecno,
                urlTarjetaPropiedad,
                fechaExtintor, estadoExtintor, urlExtintor);
    }

    /**
     * Guarda una nueva versión de documento (desactiva la fila activa anterior del mismo tipo).
     */
    @Transactional
    public void saveDocument(VehicleDocumentRequest req) {
        saveDocument(req, null);
    }

    @Transactional
    public void saveDocument(VehicleDocumentRequest req, String registradoPor) {
        if (req.idVehiculo() == null) {
            throw new IllegalArgumentException("idVehiculo es obligatorio.");
        }
        String tipoCheck = normalizeTipoForPersistence(req.tipoDocumento());
        if (req.fechaVencimiento() == null && !"TARJETA DE PROPIEDAD".equals(tipoCheck)) {
            throw new IllegalArgumentException("fechaVencimiento es obligatorio para este tipo de documento.");
        }
        if (!vehicleRepository.existsById(req.idVehiculo())) {
            throw new IllegalArgumentException("Vehículo no encontrado: id=" + req.idVehiculo());
        }
        String tipo = tipoCheck;
        LocalDate fechaEfectiva = req.fechaVencimiento();
        Optional<DocumentacionYElementosEntity> activeOpt =
                documentacionRepository.findLatestByVehiculoAndTipo(req.idVehiculo(), tipo);

        String imagenUrl = blankToNull(req.imagenUrl());
        String contentType = blankToNull(req.contentType());
        if (imagenUrl == null && activeOpt.isPresent()) {
            imagenUrl = blankToNull(activeOpt.get().getImagenUrl());
            if (contentType == null) {
                contentType = blankToNull(activeOpt.get().getContentType());
            }
        }

        if (activeOpt.isPresent()) {
            DocumentacionYElementosEntity a = activeOpt.get();
            if (Objects.equals(a.getFechaVencimiento(), fechaEfectiva)
                    && Objects.equals(blankToNull(a.getImagenUrl()), imagenUrl)) {
                return;
            }
        }

        documentacionRepository.deactivateAllActiveForVehiculoAndTipo(req.idVehiculo(), tipo);

        DocumentacionYElementosEntity doc = DocumentacionYElementosEntity.builder()
                .idVehiculo(req.idVehiculo())
                .tipoDocumento(tipo)
                .fechaVencimiento(fechaEfectiva)
                .imagenUrl(imagenUrl)
                .contentType(contentType)
                .estadoDatos(calcularEstado(fechaEfectiva))
                .activo(true)
                .registradoPor(normalizeRegistradoPor(registradoPor))
                .build();
        documentacionRepository.save(doc);
        documentacionRepository.flush();

        // Recalcular alertas preventivas de inmediato (no esperar al cron de las 5am ni
        // a un refresh manual), para que la alerta de este documento desaparezca/actualice
        // apenas se guarda la nueva fecha de vencimiento.
        preventiveAlertCalculationService.calculateAndEmitAlerts();

        saveActionUseCase.save("El usuario " + doc.getRegistradoPor() + " ha guardado el documento " + tipo
                + " del vehículo id=" + req.idVehiculo());
    }

    @Transactional
    public void saveDocumentFromUpload(Integer idVehiculo, String tipoRaw, LocalDate fecha, MultipartFile file, String username)
            throws IOException {
        if (!vehicleRepository.existsById(idVehiculo)) {
            throw new IllegalArgumentException("Vehículo no encontrado: id=" + idVehiculo);
        }
        String tipo = normalizeTipoForPersistence(tipoRaw);
        String folder = VehicleDocumentStorageService.folderSegmentForTipoBd(tipo);

        // Capturar el registro activo ANTES de mover el archivo, para luego actualizar su URL al path archivado.
        Optional<DocumentacionYElementosEntity> prevActive =
                documentacionRepository.findLatestByVehiculoAndTipo(idVehiculo, tipo);

        var stored = vehicleDocumentStorageService.store(file, idVehiculo, folder);

        // Corregir la URL del registro anterior para que apunte al archivo archivado, no al nuevo current.
        if (stored.previousArchivedUrl() != null && prevActive.isPresent()) {
            documentacionRepository.updateImagenUrlById(
                    prevActive.get().getIdDocumento(), stored.previousArchivedUrl());
        }

        saveDocument(new VehicleDocumentRequest(idVehiculo, tipo, fecha, stored.relativeUrl(), stored.contentType()), username);
    }

    @Transactional(readOnly = true)
    public List<DocumentoVehiculoVersionDTO> getDocumentHistory(Integer idVehiculo) {
        if (!vehicleRepository.existsById(idVehiculo)) {
            throw new IllegalArgumentException("Vehículo no encontrado: id=" + idVehiculo);
        }
        return documentacionRepository.findByIdVehiculoOrderByIdDocumentoDesc(idVehiculo).stream()
                .filter(row -> !isLicenciaTipo(row.getTipoDocumento()))
                .map(row -> new DocumentoVehiculoVersionDTO(
                        row.getIdDocumento(),
                        row.getTipoDocumento(),
                        row.getFechaVencimiento(),
                        resolveUrl(row.getImagenUrl()),
                        row.getContentType() != null ? row.getContentType() : guessContentTypeFromPath(row.getImagenUrl()),
                        row.getFechaRegistro(),
                        normalizeRegistradoPor(row.getRegistradoPor()),
                        row.getActivo() == null || Boolean.TRUE.equals(row.getActivo()),
                        calcularEstado(row.getFechaVencimiento())))
                .toList();
    }

    /**
     * La licencia de conducción se ligó a vehículos antes de migrarse a usuarios (ver {@code UserService}).
     * Filtra esos registros legacy de {@code documentacion_y_elementos} para que no reaparezcan en el
     * historial del vehículo.
     */
    private static boolean isLicenciaTipo(String tipoDocumento) {
        return tipoDocumento != null && tipoDocumento.trim().toUpperCase(java.util.Locale.ROOT).startsWith("LICENCIA");
    }

    /**
     * Registros antiguos pudieron persistir {@link UserPrincipal#toString()} en {@code registrado_por}
     * (p. ej. {@code UserPrincipal[id=41, username=David]}). Devuelve el login legible.
     */
    private static String normalizeRegistradoPor(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String s = raw.trim();
        if (s.startsWith("UserPrincipal[")) {
            int key = s.indexOf("username=");
            if (key >= 0) {
                int from = key + "username=".length();
                int end = s.indexOf(']', from);
                if (end > from) {
                    s = s.substring(from, end).trim();
                }
            }
        }
        if (s.isBlank()) {
            return null;
        }
        if (s.length() > 100) {
            return s.substring(0, 100);
        }
        return s;
    }

    private static String normalizeTipoForPersistence(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("tipoDocumento es obligatorio.");
        }
        String t = raw.trim().toUpperCase(Locale.ROOT).replace('_', ' ');
        return switch (t) {
            case "LICENCIA", "LICENCIA CONDUCCION", "LICENCIA DE CONDUCCION" -> "LICENCIA DE CONDUCCION";
            case "TARJETA PROPIEDAD", "TARJETA DE PROPIEDAD" -> "TARJETA DE PROPIEDAD";
            default -> t;
        };
    }

    private static String guessContentTypeFromPath(String url) {
        if (url == null) {
            return null;
        }
        String u = url.toLowerCase(Locale.ROOT);
        if (u.endsWith(".pdf")) {
            return "application/pdf";
        }
        if (u.endsWith(".png")) {
            return "image/png";
        }
        if (u.endsWith(".webp")) {
            return "image/webp";
        }
        if (u.endsWith(".jpg") || u.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        return null;
    }

    /**
     * Resuelve una URL de imagen: si es relativa, le concatena el host actual.
     */
    private String resolveUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) return null;
        if (rawUrl.toLowerCase().startsWith("http")) return rawUrl;
        
        String baseUrl = ServletUriComponentsBuilder.fromCurrentContextPath().toUriString();
        String cleanPath = rawUrl.replace("\\", "/");
        if (cleanPath.startsWith("/")) cleanPath = cleanPath.substring(1);
        
        // Lógica Universal: Si ya trae la ruta (ej: uploads/ID/foto.jpg) la usamos,
        // si es solo el nombre, asumimos que está en la carpeta de documentos.
        if (cleanPath.toLowerCase().startsWith("uploads/")) {
            return baseUrl + "/" + cleanPath;
        } else {
            return baseUrl + "/uploads/documents/" + cleanPath;
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Calcula el estado del documento comparando el MES de vencimiento
     * con el MES actual.
     */
    private String calcularEstado(LocalDate fechaVencimiento) {
        if (fechaVencimiento == null) return null;
        
        YearMonth mesVenc = YearMonth.from(fechaVencimiento);
        YearMonth mesHoy  = YearMonth.now();

        // 1. Si el mes ya pasó -> Vencido
        if (mesVenc.isBefore(mesHoy)) {
            return "Vencido";
        }

        // 2. Si el mes de vencimiento es HOY -> Vigente (para no bloquear hoy)
        if (mesVenc.equals(mesHoy)) {
            return "Vigente";
        }

        // 3. Si el mes de vencimiento es el SIGUIENTE -> Próximo a Vencer
        if (mesVenc.equals(mesHoy.plusMonths(1))) {
            return "Próximo a Vencer";
        }

        // 4. Si es después del mes siguiente
        return "Vigente";
    }

    private String booleanToSiNo(Boolean value) {
        if (value == null)
            return null;
        return value ? "Si" : "No";
    }

    public Page<VehicleInspectionReportDTO> getMotoInspectionsPaginated(Pageable pageable) {
        List<InspPreOperativaEntity> allMotoInspections = listMotoInspectionEntitiesNewestFirst();
        List<VehicleInspectionReportDTO> reportDTOs = mapToReportDTO(allMotoInspections);

        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), reportDTOs.size());
        List<VehicleInspectionReportDTO> pageContent = reportDTOs.subList(start, end);

        return new PageImpl<>(pageContent, pageable, reportDTOs.size());
    }
}