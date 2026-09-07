package com.app.usochicamochabackend.fuel.web;

import com.app.usochicamochabackend.fuel.application.dto.RefuelingRecordResponse;
import com.app.usochicamochabackend.fuel.application.port.ExportRefuelingReportUseCase;
import com.app.usochicamochabackend.fuel.application.port.GetRefuelingReportUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/fuel/refueling/reporte")
@RequiredArgsConstructor
public class RefuelingReportController {

    private final GetRefuelingReportUseCase getRefuelingReportUseCase;
    private final ExportRefuelingReportUseCase exportRefuelingReportUseCase;

    @GetMapping
    public ResponseEntity<List<RefuelingRecordResponse>> reporte(
            @RequestParam String tipo,
            @RequestParam(required = false) String area,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin) {
        return ResponseEntity.ok(getRefuelingReportUseCase.obtenerReporte(tipo, area, fechaInicio, fechaFin));
    }

    // Un solo tanqueo (por id) con el mismo enriquecimiento del reporte — evita traer
    // todo el reporte del tipo solo para abrir el modal de editar un registro puntual.
    @GetMapping("/{id}")
    public ResponseEntity<RefuelingRecordResponse> reportePorId(@PathVariable Long id) {
        return ResponseEntity.ok(getRefuelingReportUseCase.obtenerPorId(id));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportar(
            @RequestParam String tipo,
            @RequestParam(required = false) String area,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin) {
        byte[] excelData = exportRefuelingReportUseCase.exportarExcel(tipo, area, fechaInicio, fechaFin);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDispositionFormData("attachment", "tanqueos_export.xlsx");
        return ResponseEntity.ok().headers(headers).body(excelData);
    }
}
