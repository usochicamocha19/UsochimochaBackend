package com.app.usochicamochabackend.update.application.service;

import com.app.usochicamochabackend.context.application.dto.MachineCurriculumDTO;
import com.app.usochicamochabackend.context.application.dto.VehicleCurriculumDTO;
import com.app.usochicamochabackend.moto.application.dto.MotoMonitoringDTO;
import com.app.usochicamochabackend.order.application.dto.OrderWithMachineDTO;
import com.app.usochicamochabackend.order.application.dto.OrderWithVehicleDTO;
import com.app.usochicamochabackend.review.infrastructure.entity.InspectionEntity;
import com.app.usochicamochabackend.update.application.dto.ConsolidateHydraulicAndMotorOilDTO;
import com.app.usochicamochabackend.vehicle.application.dto.VehicleMonitoringDTO;
import com.app.usochicamochabackend.vehicleinspection.application.dto.VehicleInspectionReportDTO;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

@Service
@Slf4j
public class ExcelGenerationService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static String formatExecutionTime(Object orderResponse) {
        if (orderResponse == null) return "—";
        try {
            try {
                String ts = (String) orderResponse.getClass().getMethod("timeSpent").invoke(orderResponse);
                if (ts != null && !ts.isBlank()) return ts;
            } catch (NoSuchMethodException ignored) {}

            Integer hours = (Integer) orderResponse.getClass().getMethod("hoursSpent").invoke(orderResponse);
            Integer minutes = (Integer) orderResponse.getClass().getMethod("minutesSpent").invoke(orderResponse);

            if (hours == null && minutes == null) return "—";
            hours = hours != null ? hours : 0;
            minutes = minutes != null ? minutes : 0;

            if (hours == 0 && minutes == 0) return "—";
            if (hours == 0) return String.format("%dm", minutes);
            if (minutes == 0) return String.format("%dh", hours);
            return String.format("%dh %dm", hours, minutes);
        } catch (Exception e) {
            return "—";
        }
    }

    public byte[] generateConsolidatedMachinesExcel(List<ConsolidateHydraulicAndMotorOilDTO> consolidatedData) throws IOException {
        log.info("Generando archivo Excel para {} máquinas", consolidatedData.size());

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Consolidado de Máquinas");

            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);

            Row headerRow = sheet.createRow(0);
            String[] headers = {
                "Pertenece", "Maquina", "Horometro Actual", "Último Reporte",
                // Motor Oil
                "Tipo / Marca", "Cantidad (GI)", "Promedio Cambio (Horas)", "Fecha Ultimo Cambio",
                "Horómetro Último Cambio", "Horómetro Próximo Cambio", "Tiempo Último Cambio (Mes)",
                "Restante Cambio (Horas)", "Estado Actual",
                // Hydraulic Oil
                "Tipo / Marca", "Cantidad (GI)", "Promedio Cambio (Horas)", "Fecha Ultimo Cambio",
                "Horómetro Último Cambio", "Horómetro Próximo Cambio", "Tiempo Último Cambio (Mes)",
                "Restante Cambio (Horas)", "Estado Actual"
            };

            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            CellStyle dataStyle = workbook.createCellStyle();
            dataStyle.setBorderBottom(BorderStyle.THIN);
            dataStyle.setBorderTop(BorderStyle.THIN);
            dataStyle.setBorderRight(BorderStyle.THIN);
            dataStyle.setBorderLeft(BorderStyle.THIN);

            int rowNum = 1;
            for (ConsolidateHydraulicAndMotorOilDTO data : consolidatedData) {
                Row row = sheet.createRow(rowNum++);

                int colNum = 0;

                row.createCell(colNum++).setCellValue(data.machine().belongsTo());
                row.createCell(colNum++).setCellValue(data.machine().name());

                if (data.currentData() != null) {
                    row.createCell(colNum++).setCellValue(data.currentData().currentHourMeter());
                    row.createCell(colNum++).setCellValue(data.currentData().lastUpdate() != null ?
                        data.currentData().lastUpdate().format(DATETIME_FORMATTER) : "");
                } else {
                    row.createCell(colNum++).setCellValue("");
                    row.createCell(colNum++).setCellValue("");
                }

                if (data.consolidateMotorOil() != null) {
                    row.createCell(colNum++).setCellValue(data.consolidateMotorOil().brand() != null ?
                        data.consolidateMotorOil().brand().getName() : "");
                    row.createCell(colNum++).setCellValue(data.consolidateMotorOil().quantity());
                    row.createCell(colNum++).setCellValue(data.consolidateMotorOil().averageChangeHours());
                    row.createCell(colNum++).setCellValue(data.consolidateMotorOil().dateLastUpdate() != null ?
                        data.consolidateMotorOil().dateLastUpdate().format(DATE_FORMATTER) : "");
                    row.createCell(colNum++).setCellValue(data.consolidateMotorOil().hourMeterLastUpdate());
                    row.createCell(colNum++).setCellValue(data.consolidateMotorOil().hourMeterNextUpdate());
                    row.createCell(colNum++).setCellValue(data.consolidateMotorOil().timeLastUpdateMouths());
                    row.createCell(colNum++).setCellValue(data.consolidateMotorOil().remainingHoursNextUpdateMouths());
                    row.createCell(colNum++).setCellValue(data.consolidateMotorOil().status());
                } else {
                    for (int i = 0; i < 9; i++) {
                        row.createCell(colNum++).setCellValue("");
                    }
                }

                if (data.consolidateHydraulicOil() != null) {
                    row.createCell(colNum++).setCellValue(data.consolidateHydraulicOil().brand() != null ?
                        data.consolidateHydraulicOil().brand().getName() : "");
                    row.createCell(colNum++).setCellValue(data.consolidateHydraulicOil().quantity());
                    row.createCell(colNum++).setCellValue(data.consolidateHydraulicOil().averageChangeHours());
                    row.createCell(colNum++).setCellValue(data.consolidateHydraulicOil().dateLastUpdate() != null ?
                        data.consolidateHydraulicOil().dateLastUpdate().format(DATE_FORMATTER) : "");
                    row.createCell(colNum++).setCellValue(data.consolidateHydraulicOil().hourMeterLastUpdate());
                    row.createCell(colNum++).setCellValue(data.consolidateHydraulicOil().hourMeterNextUpdate());
                    row.createCell(colNum++).setCellValue(data.consolidateHydraulicOil().timeLastUpdateMouths());
                    row.createCell(colNum++).setCellValue(data.consolidateHydraulicOil().remainingHoursNextUpdateMouths());
                    row.createCell(colNum++).setCellValue(data.consolidateHydraulicOil().status());
                } else {
                    for (int i = 0; i < 9; i++) {
                        row.createCell(colNum++).setCellValue("");
                    }
                }

                for (int i = 0; i < headers.length; i++) {
                    row.getCell(i).setCellStyle(dataStyle);
                }
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
                // Establecer un mínimo ancho para evitar columnas muy estrechas
                if (sheet.getColumnWidth(i) < 3000) {
                    sheet.setColumnWidth(i, 3000);
                }
                // Máximo ancho para evitar columnas excesivamente anchas
                if (sheet.getColumnWidth(i) > 8000) {
                    sheet.setColumnWidth(i, 8000);
                }
            }

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);
            workbook.close();

            log.info("Archivo Excel generado exitosamente con {} filas de datos", consolidatedData.size());
            return outputStream.toByteArray();
        }
    }

    public byte[] generateMachineCurriculumExcel(List<MachineCurriculumDTO> curriculumData) throws IOException {
        log.info("Generando archivo Excel para curriculum de {} máquinas", curriculumData.size());

        try (Workbook workbook = new XSSFWorkbook()) {
            for (MachineCurriculumDTO curriculum : curriculumData) {
                Sheet sheet = workbook.createSheet(curriculum.machine().name());

                // Styles
                CellStyle headerStyle = workbook.createCellStyle();
                Font headerFont = workbook.createFont();
                headerFont.setBold(true);
                headerFont.setColor(IndexedColors.WHITE.getIndex());
                headerStyle.setFont(headerFont);
                headerStyle.setFillForegroundColor(IndexedColors.BLUE.getIndex());
                headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                headerStyle.setAlignment(HorizontalAlignment.CENTER);
                headerStyle.setBorderBottom(BorderStyle.THIN);
                headerStyle.setBorderTop(BorderStyle.THIN);
                headerStyle.setBorderRight(BorderStyle.THIN);
                headerStyle.setBorderLeft(BorderStyle.THIN);

                CellStyle dataStyle = workbook.createCellStyle();
                dataStyle.setBorderBottom(BorderStyle.THIN);
                dataStyle.setBorderTop(BorderStyle.THIN);
                dataStyle.setBorderRight(BorderStyle.THIN);
                dataStyle.setBorderLeft(BorderStyle.THIN);

                // First table: Machine info
                int rowNum = 0;
                Row machineHeaderRow = sheet.createRow(rowNum++);
                String[] machineHeaders = {"Equipo", "N Interno de identificacion", "Modelo", "Numero de Motor", "Vigencia soat", "Vigencia runt"};
                for (int i = 0; i < machineHeaders.length; i++) {
                    Cell cell = machineHeaderRow.createCell(i);
                    cell.setCellValue(machineHeaders[i]);
                    cell.setCellStyle(headerStyle);
                }

                Row machineDataRow = sheet.createRow(rowNum++);
                machineDataRow.createCell(0).setCellValue(curriculum.machine().name());
                machineDataRow.createCell(1).setCellValue(curriculum.machine().numInterIdentification());
                machineDataRow.createCell(2).setCellValue(curriculum.machine().model());
                machineDataRow.createCell(3).setCellValue(curriculum.machine().numEngine());
                machineDataRow.createCell(4).setCellValue(curriculum.machine().soat() != null ? curriculum.machine().soat().format(DATE_FORMATTER) : "");
                machineDataRow.createCell(5).setCellValue(curriculum.machine().runt() != null ? curriculum.machine().runt().format(DATE_FORMATTER) : "");

                for (int i = 0; i < machineHeaders.length; i++) {
                    machineDataRow.getCell(i).setCellStyle(dataStyle);
                }

                // Skip two rows
                rowNum += 2;

                // Second table: Results
                Row resultsHeaderRow = sheet.createRow(rowNum++);
                String[] resultsHeaders = {"Fecha", "Horometro", "Descripcion", "REF", "Nombre", "Cantidad", "Valor", "Mecanico de planta", "Contratista", "Tiempo empleado", "Valor", "Valor total", "Descripcion mano de obre"};
                for (int i = 0; i < resultsHeaders.length; i++) {
                    Cell cell = resultsHeaderRow.createCell(i);
                    cell.setCellValue(resultsHeaders[i]);
                    cell.setCellStyle(headerStyle);
                }

                for (var inspection : curriculum.inspections()) {
                    for (var result : inspection.results()) {
                        Row row = sheet.createRow(rowNum++);
                        int colNum = 0;

                        // Fecha
                        Cell cell0 = row.createCell(colNum++);
                        cell0.setCellValue(inspection.dateStamp() != null ? inspection.dateStamp().format(DATETIME_FORMATTER) : "");

                        row.createCell(colNum++).setCellValue(inspection.hourMeter());

                    // Descripcion
                    row.createCell(colNum++).setCellValue(result.description());

                    // REF
                    row.createCell(colNum++).setCellValue(result.sparePart() != null ? result.sparePart().ref() : "");

                    // Nombre
                    row.createCell(colNum++).setCellValue(result.sparePart() != null ? result.sparePart().name() : "");

                    // Cantidad
                    row.createCell(colNum++).setCellValue(result.sparePart() != null ? result.sparePart().quantity() : "");

                    // Valor (spare part)
                    row.createCell(colNum++).setCellValue(result.sparePart() != null ? result.sparePart().price().toString() : "");

                    // Mecanico de planta
                    row.createCell(colNum++).setCellValue(result.labor() != null && result.labor().user() != null ? result.labor().user().getFullName() : "");

                    // Contratista
                    row.createCell(colNum++).setCellValue(result.labor() != null ? result.labor().contractor() : "");

                    // Tiempo empleado
                    row.createCell(colNum++).setCellValue(result.timeSpent());

                    // Valor (labor)
                    row.createCell(colNum++).setCellValue(result.labor() != null ? result.labor().price().toString() : "");

                    // Valor total
                    row.createCell(colNum++).setCellValue(result.totalPrice().toString());

                    row.createCell(colNum++).setCellValue(result.labor() != null ? result.labor().observations() : "");

                        for (int i = 0; i < resultsHeaders.length; i++) {
                            row.getCell(i).setCellStyle(dataStyle);
                        }
                    }
                }

                // Auto-size columns
                for (int i = 0; i < resultsHeaders.length; i++) {
                    sheet.autoSizeColumn(i);
                    if (sheet.getColumnWidth(i) < 3000) {
                        sheet.setColumnWidth(i, 3000);
                    }
                    if (sheet.getColumnWidth(i) > 8000) {
                        sheet.setColumnWidth(i, 8000);
                    }
                }
            }

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);
            workbook.close();

            log.info("Archivo Excel de curriculum generado exitosamente con {} máquinas", curriculumData.size());
            return outputStream.toByteArray();
        }
    }

    public byte[] generateInspectionsExcel(List<InspectionEntity> inspections) throws IOException {
        log.info("Generando archivo Excel para {} inspecciones", inspections.size());

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Inspecciones");

            // Styles
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);

            CellStyle dataStyle = workbook.createCellStyle();
            dataStyle.setBorderBottom(BorderStyle.THIN);
            dataStyle.setBorderTop(BorderStyle.THIN);
            dataStyle.setBorderRight(BorderStyle.THIN);
            dataStyle.setBorderLeft(BorderStyle.THIN);

            // Headers
            Row headerRow = sheet.createRow(0);
            String[] headers = {
                "Mes", "Marca Temporal", "Maquina", "Horometro", "Fugas en el Sistema",
                "Sistema de Frenos", "Estado de Correas y Poleas", "Estado de Llantas y/o Carriles",
                "Sistema de Encendido", "Sistema Eléctrico", "Sistema Mecánico", "Nivel de Temperatura",
                "Nivel de Aceite", "Nivel de Hidraulico", "Nivel de Refrigerante", "Estado Estructural en General",
                "Vigencia EXTINTOR", "Observaciones", "Engrasado", "Observaciones Engrasado", "Responsable Inspección"
            };

            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Data rows
            int rowNum = 1;
            for (InspectionEntity inspection : inspections) {
                Row row = sheet.createRow(rowNum++);
                int colNum = 0;

                // Mes
                String month = inspection.getDateStamp().getMonth().getDisplayName(TextStyle.FULL, Locale.getDefault());
                row.createCell(colNum++).setCellValue(month);

                // Marca Temporal
                row.createCell(colNum++).setCellValue(inspection.getDateStamp().format(DATETIME_FORMATTER));

                // Maquina
                row.createCell(colNum++).setCellValue(inspection.getMachine() != null ? inspection.getMachine().getName() : "");

                // Horometro
                row.createCell(colNum++).setCellValue(inspection.getHourMeter() != null ? inspection.getHourMeter().toString() : "");

                // Status fields
                row.createCell(colNum++).setCellValue(inspection.getLeakStatus() != null ? inspection.getLeakStatus() : "");
                row.createCell(colNum++).setCellValue(inspection.getBrakeStatus() != null ? inspection.getBrakeStatus() : "");
                row.createCell(colNum++).setCellValue(inspection.getBeltsPulleysStatus() != null ? inspection.getBeltsPulleysStatus() : "");
                row.createCell(colNum++).setCellValue(inspection.getTireLanesStatus() != null ? inspection.getTireLanesStatus() : "");
                row.createCell(colNum++).setCellValue(inspection.getCarIgnitionStatus() != null ? inspection.getCarIgnitionStatus() : "");
                row.createCell(colNum++).setCellValue(inspection.getElectricalStatus() != null ? inspection.getElectricalStatus() : "");
                row.createCell(colNum++).setCellValue(inspection.getMechanicalStatus() != null ? inspection.getMechanicalStatus() : "");
                row.createCell(colNum++).setCellValue(inspection.getTemperatureStatus() != null ? inspection.getTemperatureStatus() : "");
                row.createCell(colNum++).setCellValue(inspection.getOilStatus() != null ? inspection.getOilStatus() : "");
                row.createCell(colNum++).setCellValue(inspection.getHydraulicStatus() != null ? inspection.getHydraulicStatus() : "");
                row.createCell(colNum++).setCellValue(inspection.getCoolantStatus() != null ? inspection.getCoolantStatus() : "");
                row.createCell(colNum++).setCellValue(inspection.getStructuralStatus() != null ? inspection.getStructuralStatus() : "");

                // Vigencia EXTINTOR
                row.createCell(colNum++).setCellValue(inspection.getExpirationDateFireExtinguisher() != null ? inspection.getExpirationDateFireExtinguisher() : "");

                // Observaciones
                row.createCell(colNum++).setCellValue(inspection.getObservations() != null ? inspection.getObservations() : "");

                // Engrasado
                row.createCell(colNum++).setCellValue(inspection.getGreasingAction() != null ? inspection.getGreasingAction() : "");

                // Observaciones Engrasado
                row.createCell(colNum++).setCellValue(inspection.getGreasingObservations() != null ? inspection.getGreasingObservations() : "");

                // Responsable Inspección
                row.createCell(colNum++).setCellValue(inspection.getUser() != null ? inspection.getUser().getFullName() : "");

                // Apply style to all cells in the row
                for (int i = 0; i < headers.length; i++) {
                    row.getCell(i).setCellStyle(dataStyle);
                }
            }

            // Auto-size columns
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
                if (sheet.getColumnWidth(i) < 3000) {
                    sheet.setColumnWidth(i, 3000);
                }
                if (sheet.getColumnWidth(i) > 8000) {
                    sheet.setColumnWidth(i, 8000);
                }
            }

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);
            workbook.close();

            log.info("Archivo Excel de inspecciones generado exitosamente con {} filas de datos", inspections.size());
            return outputStream.toByteArray();
        }
    }

    public byte[] generateVehicleConsolidatedExcel(List<VehicleMonitoringDTO> data) throws IOException {
        log.info("Generando Excel consolidado vehículos: {} registros", data.size());

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Consolidado Vehículos");

            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);

            CellStyle dataStyle = workbook.createCellStyle();
            dataStyle.setBorderBottom(BorderStyle.THIN);
            dataStyle.setBorderTop(BorderStyle.THIN);
            dataStyle.setBorderRight(BorderStyle.THIN);
            dataStyle.setBorderLeft(BorderStyle.THIN);

            String[] headers = {
                "Pertenece a", "Placa", "Km Actual", "Días sin Reporte", "Fecha Último Reporte",
                // Aceite Motor (igual al consolidado en pantalla)
                "Marca", "Cant.", "Intervalo", "Fecha Últ. Cambio", "Km Últ. Cambio",
                "Próximo Cambio", "Días desde Cambio", "Km Restantes", "Filtro Aire", "Estado"
            };

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowNum = 1;
            for (VehicleMonitoringDTO dto : data) {
                Row row = sheet.createRow(rowNum++);
                int c = 0;

                row.createCell(c++).setCellValue(dto.area() != null ? dto.area() : "");
                row.createCell(c++).setCellValue(dto.placa() != null ? dto.placa() : "");
                row.createCell(c++).setCellValue(dto.kmActual() != null ? dto.kmActual() : 0);
                row.createCell(c++).setCellValue(dto.diasUltimoReporte() != null ? dto.diasUltimoReporte() : 0);
                row.createCell(c++).setCellValue(dto.fechaUltimoReporte() != null ? dto.fechaUltimoReporte().format(DATETIME_FORMATTER) : "");

                VehicleMonitoringDTO.OilStatus oil = dto.maintenance();
                row.createCell(c++).setCellValue(oil != null && oil.brandName() != null ? oil.brandName() : "");
                row.createCell(c++).setCellValue(oil != null && oil.quantity() != null ? oil.quantity() : 0);
                row.createCell(c++).setCellValue(oil != null && oil.intervalKm() != null ? oil.intervalKm() : 0);
                row.createCell(c++).setCellValue(oil != null && oil.fechaUltimoCambio() != null ? oil.fechaUltimoCambio().format(DATE_FORMATTER) : "");
                row.createCell(c++).setCellValue(oil != null && oil.kmCambio() != null ? oil.kmCambio() : 0);
                row.createCell(c++).setCellValue(oil != null && oil.kmProximoCambio() != null ? oil.kmProximoCambio() : 0);
                row.createCell(c++).setCellValue(oil != null && oil.diasDesdeUltimoCambio() != null ? oil.diasDesdeUltimoCambio() : 0);
                row.createCell(c++).setCellValue(oil != null && oil.kmParaProximo() != null ? oil.kmParaProximo() : 0);
                row.createCell(c++).setCellValue(oil != null && oil.filtroAire() != null ? (oil.filtroAire() ? "Sí" : "No") : "");
                row.createCell(c++).setCellValue(oil != null && oil.estado() != null ? oil.estado() : "");

                for (int i = 0; i < headers.length; i++) {
                    row.getCell(i).setCellStyle(dataStyle);
                }
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
                if (sheet.getColumnWidth(i) < 3000) sheet.setColumnWidth(i, 3000);
                if (sheet.getColumnWidth(i) > 8000) sheet.setColumnWidth(i, 8000);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    public byte[] generateMotoConsolidatedExcel(List<MotoMonitoringDTO> data) throws IOException {
        log.info("Generando Excel consolidado motos: {} registros", data.size());

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Consolidado Motos");

            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);

            CellStyle dataStyle = workbook.createCellStyle();
            dataStyle.setBorderBottom(BorderStyle.THIN);
            dataStyle.setBorderTop(BorderStyle.THIN);
            dataStyle.setBorderRight(BorderStyle.THIN);
            dataStyle.setBorderLeft(BorderStyle.THIN);

            String[] headers = {
                "Pertenece a", "Placa", "Km Actual", "Días sin Reporte", "Fecha Último Reporte",
                // Aceite Motor (igual al consolidado en pantalla)
                "Marca", "Cant.", "Intervalo", "Fecha Últ. Cambio", "Km Últ. Cambio",
                "Próximo Cambio", "Días desde Cambio", "Km Restantes", "Filtro Aire", "Estado"
            };

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowNum = 1;
            for (MotoMonitoringDTO dto : data) {
                Row row = sheet.createRow(rowNum++);
                int c = 0;

                row.createCell(c++).setCellValue(dto.departamento() != null ? dto.departamento() : "");
                row.createCell(c++).setCellValue(dto.placa() != null ? dto.placa() : "");
                row.createCell(c++).setCellValue(dto.kmActual() != null ? dto.kmActual() : 0);
                row.createCell(c++).setCellValue(dto.diasUltimoReporte() != null ? dto.diasUltimoReporte() : 0);
                row.createCell(c++).setCellValue(dto.fechaUltimoReporte() != null ? dto.fechaUltimoReporte().format(DATETIME_FORMATTER) : "");

                MotoMonitoringDTO.OilStatus oil = dto.oil();
                row.createCell(c++).setCellValue(oil != null && oil.brandName() != null ? oil.brandName() : "");
                row.createCell(c++).setCellValue(oil != null && oil.quantity() != null ? oil.quantity() : 0);
                row.createCell(c++).setCellValue(oil != null && oil.intervalKm() != null ? oil.intervalKm() : 0);
                row.createCell(c++).setCellValue(oil != null && oil.fechaUltimoCambio() != null ? oil.fechaUltimoCambio().format(DATE_FORMATTER) : "");
                row.createCell(c++).setCellValue(oil != null && oil.kmCambio() != null ? oil.kmCambio() : 0);
                row.createCell(c++).setCellValue(oil != null && oil.kmProximoCambio() != null ? oil.kmProximoCambio() : 0);
                row.createCell(c++).setCellValue(oil != null && oil.diasDesdeUltimoCambio() != null ? oil.diasDesdeUltimoCambio() : 0);
                row.createCell(c++).setCellValue(oil != null && oil.kmParaProximo() != null ? oil.kmParaProximo() : 0);
                row.createCell(c++).setCellValue(oil != null && oil.filtroAire() != null ? (oil.filtroAire() ? "Sí" : "No") : "");
                row.createCell(c++).setCellValue(oil != null && oil.estado() != null ? oil.estado() : "");

                for (int i = 0; i < headers.length; i++) {
                    row.getCell(i).setCellStyle(dataStyle);
                }
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
                if (sheet.getColumnWidth(i) < 3000) sheet.setColumnWidth(i, 3000);
                if (sheet.getColumnWidth(i) > 8000) sheet.setColumnWidth(i, 8000);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    public byte[] generateMachineOrdersExcel(List<OrderWithMachineDTO> orders) throws IOException {
        log.info("Generando Excel órdenes maquinaria: {} registros", orders.size());

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Órdenes Maquinaria");

            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);

            CellStyle dataStyle = workbook.createCellStyle();
            dataStyle.setBorderBottom(BorderStyle.THIN);
            dataStyle.setBorderTop(BorderStyle.THIN);
            dataStyle.setBorderRight(BorderStyle.THIN);
            dataStyle.setBorderLeft(BorderStyle.THIN);

            String[] headers = {
                "Consecutivo", "Fecha", "Estado", "Descripción",
                "Asignado por", "Máquina", "Área", "Proveedor Repuesto", "Tiempo"
            };

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowNum = 1;
            for (OrderWithMachineDTO dto : orders) {
                Row row = sheet.createRow(rowNum++);
                int c = 0;
                var order = dto.order();
                var machine = dto.machine();
                row.createCell(c++).setCellValue(order != null && order.consecutive() != null ? order.consecutive() : "");
                row.createCell(c++).setCellValue(order != null && order.date() != null ? order.date().format(DATETIME_FORMATTER) : "");
                row.createCell(c++).setCellValue(order != null && order.status() != null ? order.status() : "");
                row.createCell(c++).setCellValue(order != null && order.description() != null ? order.description() : "");
                row.createCell(c++).setCellValue(order != null && order.assignerUser() != null ? order.assignerUser().fullName() : "");
                row.createCell(c++).setCellValue(machine != null && machine.name() != null ? machine.name() : "");
                row.createCell(c++).setCellValue(machine != null && machine.belongsTo() != null ? machine.belongsTo() : "");
                row.createCell(c++).setCellValue(order != null && order.suppliers() != null ? order.suppliers() : "");
                row.createCell(c++).setCellValue(formatExecutionTime(order));

                for (int i = 0; i < headers.length; i++) {
                    row.getCell(i).setCellStyle(dataStyle);
                }
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
                if (sheet.getColumnWidth(i) < 3000) sheet.setColumnWidth(i, 3000);
                if (sheet.getColumnWidth(i) > 8000) sheet.setColumnWidth(i, 8000);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    public byte[] generateVehicleOrdersExcel(List<OrderWithVehicleDTO> orders) throws IOException {
        log.info("Generando Excel órdenes vehículos/motos: {} registros", orders.size());

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Órdenes Vehículos");

            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);

            CellStyle dataStyle = workbook.createCellStyle();
            dataStyle.setBorderBottom(BorderStyle.THIN);
            dataStyle.setBorderTop(BorderStyle.THIN);
            dataStyle.setBorderRight(BorderStyle.THIN);
            dataStyle.setBorderLeft(BorderStyle.THIN);

            String[] headers = {
                "Consecutivo", "Fecha", "Estado", "Descripción",
                "Asignado por", "Placa", "Marca", "Tipo Vehículo", "Fecha Inspección", "Proveedor Repuesto", "Tiempo"
            };

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowNum = 1;
            for (OrderWithVehicleDTO dto : orders) {
                Row row = sheet.createRow(rowNum++);
                int c = 0;
                var order = dto.order();
                var vehicle = dto.vehicle();
                row.createCell(c++).setCellValue(order != null && order.consecutive() != null ? order.consecutive() : "");
                row.createCell(c++).setCellValue(order != null && order.date() != null ? order.date().format(DATETIME_FORMATTER) : "");
                row.createCell(c++).setCellValue(order != null && order.status() != null ? order.status() : "");
                row.createCell(c++).setCellValue(order != null && order.description() != null ? order.description() : "");
                row.createCell(c++).setCellValue(order != null && order.assignerUser() != null ? order.assignerUser().fullName() : "");
                row.createCell(c++).setCellValue(vehicle != null && vehicle.placa() != null ? vehicle.placa() : "");
                row.createCell(c++).setCellValue(vehicle != null && vehicle.marca() != null ? vehicle.marca() : "");
                row.createCell(c++).setCellValue(vehicle != null && vehicle.tipoVehiculo() != null ? vehicle.tipoVehiculo() : "");
                row.createCell(c++).setCellValue(vehicle != null && vehicle.fechaInspeccion() != null ? vehicle.fechaInspeccion().format(DATETIME_FORMATTER) : "");
                row.createCell(c++).setCellValue(order != null && order.suppliers() != null ? order.suppliers() : "");
                row.createCell(c++).setCellValue(formatExecutionTime(order));

                for (int i = 0; i < headers.length; i++) {
                    row.getCell(i).setCellStyle(dataStyle);
                }
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
                if (sheet.getColumnWidth(i) < 3000) sheet.setColumnWidth(i, 3000);
                if (sheet.getColumnWidth(i) > 8000) sheet.setColumnWidth(i, 8000);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    public byte[] generateVehicleInspectionsExcel(List<VehicleInspectionReportDTO> inspections) throws IOException {
        log.info("Generando Excel de inspecciones de vehículos/motos: {} registros", inspections.size());

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Inspecciones");

            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);

            CellStyle dataStyle = workbook.createCellStyle();
            dataStyle.setBorderBottom(BorderStyle.THIN);
            dataStyle.setBorderTop(BorderStyle.THIN);
            dataStyle.setBorderRight(BorderStyle.THIN);
            dataStyle.setBorderLeft(BorderStyle.THIN);

            String[] headers = {
                "Fecha Registro", "Placa", "Marca", "Tipo Vehículo", "Área", "Ubicación",
                "Responsable", "Kilometraje", "Aprobado Ruta",
                // Mecánico
                "Nivel Aceite", "Nivel Refrigerante", "Nivel Frenos", "Estado Llantas",
                "Luces", "Estado Visual", "Limpieza",
                // Documentos
                "Check SOAT", "Check Tecnomecánica", "Check Extintor",
                // Elementos
                "Botiquín", "Señalización", "Líneas Emergencia", "Llanta Repuesto", "Gato Hidráulico",
                // Salud
                "Salud Física", "Salud Mental", "Sobriedad", "Medicamentos",
                "Condición para Conducir", "Consciente Responsabilidad",
                // Mano de Obra
                "Mecánico de Planta", "Contratista", "Tiempo Empleado",
                // Observaciones
                "Observaciones"
            };

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowNum = 1;
            for (VehicleInspectionReportDTO dto : inspections) {
                Row row = sheet.createRow(rowNum++);
                int c = 0;

                row.createCell(c++).setCellValue(dto.fechaRegistro() != null ? dto.fechaRegistro().format(DATETIME_FORMATTER) : "");
                row.createCell(c++).setCellValue(dto.placa() != null ? dto.placa() : "");
                row.createCell(c++).setCellValue(dto.marca() != null ? dto.marca() : "");
                row.createCell(c++).setCellValue(dto.tipoVehiculo() != null ? dto.tipoVehiculo() : "");
                row.createCell(c++).setCellValue(dto.areaOrganizacional() != null ? dto.areaOrganizacional() : "");
                row.createCell(c++).setCellValue(dto.ubicacion() != null ? dto.ubicacion() : "");
                row.createCell(c++).setCellValue(dto.responsable() != null ? dto.responsable() : "");
                row.createCell(c++).setCellValue(dto.kilometraje() != null ? dto.kilometraje() : 0);
                row.createCell(c++).setCellValue(dto.aprobadoRuta() != null ? (dto.aprobadoRuta() ? "Sí" : "No") : "");

                row.createCell(c++).setCellValue(dto.nivelAceite() != null ? dto.nivelAceite() : "");
                row.createCell(c++).setCellValue(dto.nivelRefrigerante() != null ? dto.nivelRefrigerante() : "");
                row.createCell(c++).setCellValue(dto.nivelFrenos() != null ? dto.nivelFrenos() : "");
                row.createCell(c++).setCellValue(dto.estadoLlantas() != null ? dto.estadoLlantas() : "");
                row.createCell(c++).setCellValue(dto.lucesGeneral() != null ? dto.lucesGeneral() : "");
                row.createCell(c++).setCellValue(dto.estadoVisual() != null ? dto.estadoVisual() : "");
                row.createCell(c++).setCellValue(dto.limpiezaGeneral() != null ? dto.limpiezaGeneral() : "");

                row.createCell(c++).setCellValue(dto.checkSoat() != null ? dto.checkSoat() : "");
                row.createCell(c++).setCellValue(dto.checkTecno() != null ? dto.checkTecno() : "");
                row.createCell(c++).setCellValue(dto.checkExtintor() != null ? dto.checkExtintor() : "");

                row.createCell(c++).setCellValue(dto.tieneBotiquin() != null ? (dto.tieneBotiquin() ? "Sí" : "No") : "");
                row.createCell(c++).setCellValue(dto.tieneSeñalizacion() != null ? (dto.tieneSeñalizacion() ? "Sí" : "No") : "");
                row.createCell(c++).setCellValue(dto.tieneLineasEmergencia() != null ? (dto.tieneLineasEmergencia() ? "Sí" : "No") : "");
                row.createCell(c++).setCellValue(dto.tieneLlantaRepuesto() != null ? dto.tieneLlantaRepuesto() : "");
                row.createCell(c++).setCellValue(dto.tieneGatoHidraulico() != null ? dto.tieneGatoHidraulico() : "");

                row.createCell(c++).setCellValue(dto.saludFisica() != null ? (dto.saludFisica() ? "Sí" : "No") : "");
                row.createCell(c++).setCellValue(dto.saludMental() != null ? (dto.saludMental() ? "Sí" : "No") : "");
                row.createCell(c++).setCellValue(dto.sobrio() != null ? (dto.sobrio() ? "Sí" : "No") : "");
                row.createCell(c++).setCellValue(dto.medicamentos() != null ? (dto.medicamentos() ? "Sí" : "No") : "");
                row.createCell(c++).setCellValue(dto.condicionParaConducir() != null ? (dto.condicionParaConducir() ? "Sí" : "No") : "");
                row.createCell(c++).setCellValue(dto.conscienteResponsabilidad() != null ? (dto.conscienteResponsabilidad() ? "Sí" : "No") : "");

                // Mano de Obra
                row.createCell(c++).setCellValue(dto.mechanicName() != null ? dto.mechanicName() : "");
                row.createCell(c++).setCellValue(dto.contractorName() != null ? dto.contractorName() : "");
                row.createCell(c++).setCellValue(dto.timeSpent() != null ? dto.timeSpent() : "");

                // Observaciones
                row.createCell(c++).setCellValue(dto.observacionesFinales() != null ? dto.observacionesFinales() : "");

                for (int i = 0; i < headers.length; i++) {
                    row.getCell(i).setCellStyle(dataStyle);
                }
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
                if (sheet.getColumnWidth(i) < 3000) sheet.setColumnWidth(i, 3000);
                if (sheet.getColumnWidth(i) > 8000) sheet.setColumnWidth(i, 8000);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    public byte[] generateVehicleCurriculumExcel(List<VehicleCurriculumDTO> curriculumData) throws IOException {
        log.info("Generando archivo Excel para curriculum de {} vehículos", curriculumData.size());

        try (Workbook workbook = new XSSFWorkbook()) {
            for (VehicleCurriculumDTO curriculum : curriculumData) {
                String sheetName = curriculum.vehicle().placa() != null ? curriculum.vehicle().placa() : "SIN_PLACA";
                if (sheetName.length() > 31) sheetName = sheetName.substring(0, 31);
                Sheet sheet = workbook.createSheet(sheetName);

                CellStyle headerStyle = workbook.createCellStyle();
                Font headerFont = workbook.createFont();
                headerFont.setBold(true);
                headerFont.setColor(IndexedColors.WHITE.getIndex());
                headerStyle.setFont(headerFont);
                headerStyle.setFillForegroundColor(IndexedColors.BLUE.getIndex());
                headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                headerStyle.setAlignment(HorizontalAlignment.CENTER);
                headerStyle.setBorderBottom(BorderStyle.THIN);
                headerStyle.setBorderTop(BorderStyle.THIN);
                headerStyle.setBorderRight(BorderStyle.THIN);
                headerStyle.setBorderLeft(BorderStyle.THIN);

                CellStyle dataStyle = workbook.createCellStyle();
                dataStyle.setBorderBottom(BorderStyle.THIN);
                dataStyle.setBorderTop(BorderStyle.THIN);
                dataStyle.setBorderRight(BorderStyle.THIN);
                dataStyle.setBorderLeft(BorderStyle.THIN);

                int rowNum = 0;

                // Vehicle info header
                Row vHeaderRow = sheet.createRow(rowNum++);
                String[] vHeaders = {"Placa", "Marca", "Tipo", "Km Actual", "Área", "Ubicación"};
                for (int i = 0; i < vHeaders.length; i++) {
                    Cell cell = vHeaderRow.createCell(i);
                    cell.setCellValue(vHeaders[i]);
                    cell.setCellStyle(headerStyle);
                }

                // Vehicle info data
                Row vDataRow = sheet.createRow(rowNum++);
                vDataRow.createCell(0).setCellValue(curriculum.vehicle().placa() != null ? curriculum.vehicle().placa() : "");
                vDataRow.createCell(1).setCellValue(curriculum.vehicle().marca() != null ? curriculum.vehicle().marca() : "");
                vDataRow.createCell(2).setCellValue(curriculum.vehicle().tipoVehiculo() != null ? curriculum.vehicle().tipoVehiculo() : "");
                vDataRow.createCell(3).setCellValue(curriculum.vehicle().kilometrajeActual() != null ? curriculum.vehicle().kilometrajeActual() : 0);
                vDataRow.createCell(4).setCellValue(curriculum.vehicle().belongsTo() != null ? curriculum.vehicle().belongsTo() : "");
                vDataRow.createCell(5).setCellValue(curriculum.vehicle().ubicacionBase() != null ? curriculum.vehicle().ubicacionBase() : "");
                for (int i = 0; i < vHeaders.length; i++) {
                    vDataRow.getCell(i).setCellStyle(dataStyle);
                }

                rowNum += 2;

                // Results header
                Row rHeaderRow = sheet.createRow(rowNum++);
                String[] rHeaders = {"Fecha", "Kilómetro", "Descripción", "REF", "Nombre repuesto", "Cantidad", "Valor repuesto", "Mecánico de planta", "Contratista", "Tiempo empleado", "Valor mano de obra", "Valor total", "Observaciones"};
                for (int i = 0; i < rHeaders.length; i++) {
                    Cell cell = rHeaderRow.createCell(i);
                    cell.setCellValue(rHeaders[i]);
                    cell.setCellStyle(headerStyle);
                }

                // Results data
                for (var result : curriculum.results()) {
                    Row row = sheet.createRow(rowNum++);
                    int colNum = 0;
                    row.createCell(colNum++).setCellValue(result.date() != null ? result.date().format(DATETIME_FORMATTER) : "");
                    row.createCell(colNum++).setCellValue(result.hourMeter() != null ? result.hourMeter() : 0);
                    row.createCell(colNum++).setCellValue(result.description() != null ? result.description() : "");
                    row.createCell(colNum++).setCellValue(result.sparePart() != null ? result.sparePart().ref() : "");
                    row.createCell(colNum++).setCellValue(result.sparePart() != null ? result.sparePart().name() : "");
                    row.createCell(colNum++).setCellValue(result.sparePart() != null ? result.sparePart().quantity() : "");
                    row.createCell(colNum++).setCellValue(result.sparePart() != null ? result.sparePart().price().toString() : "");
                    row.createCell(colNum++).setCellValue(result.labor() != null && result.labor().user() != null ? result.labor().user().getFullName() : "");
                    row.createCell(colNum++).setCellValue(result.labor() != null ? result.labor().contractor() : "");
                    row.createCell(colNum++).setCellValue(result.timeSpent() != null ? result.timeSpent() : "");
                    row.createCell(colNum++).setCellValue(result.labor() != null ? result.labor().price().toString() : "");
                    row.createCell(colNum++).setCellValue(result.totalPrice() != null ? result.totalPrice().toString() : "");
                    row.createCell(colNum++).setCellValue(result.labor() != null && result.labor().observations() != null ? result.labor().observations() : "");
                    for (int i = 0; i < rHeaders.length; i++) {
                        row.getCell(i).setCellStyle(dataStyle);
                    }
                }

                int maxCols = Math.max(vHeaders.length, rHeaders.length);
                for (int i = 0; i < maxCols; i++) {
                    sheet.autoSizeColumn(i);
                    if (sheet.getColumnWidth(i) < 3000) sheet.setColumnWidth(i, 3000);
                    if (sheet.getColumnWidth(i) > 8000) sheet.setColumnWidth(i, 8000);
                }
            }

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);
            log.info("Archivo Excel de curriculum de vehículos generado exitosamente con {} hojas", curriculumData.size());
            return outputStream.toByteArray();
        }
    }
}