package com.app.usochicamochabackend.shared.calculator;

public class OilChangeMachineAlertCalculator {

    public static class Result {
        public String colorEstado;
        public Double percentageUsed;
        public Integer horasRemaining;
        public String descripcion;

        public Result(String colorEstado, Double percentageUsed, Integer horasRemaining, String descripcion) {
            this.colorEstado = colorEstado;
            this.percentageUsed = percentageUsed;
            this.horasRemaining = horasRemaining;
            this.descripcion = descripcion;
        }
    }

    /**
     * Calcula alerta para CAMBIO DE ACEITE (maquinaria por HORÓMETRO).
     * Exactamente igual que vehículos pero con HORAS en lugar de KM.
     * Retorna: { colorEstado, percentageUsed, horasRemaining, descripcion }
     *
     * Lógica de colores (igual a vehículos):
     * - Si percentageUsed >= 100 → ROJO (ya pasó el cambio)
     * - Si percentageUsed >= 90 → ROJO (crítico)
     * - Si percentageUsed >= 70 → AMARILLO (advertencia)
     * - Si percentageUsed < 70 → VERDE (no se guarda)
     *
     * Datos de entrada:
     * - horasActual: horómetro actual (MachineEntity.horometroActual)
     * - hourStamp: horas cuando se hizo el último cambio (OilChangeEntity.hourStamp)
     * - hourRange: intervalo entre cambios en horas
     * - oilType: "MOTOR" o "HYDRAULIC" (para el mensaje)
     */
    public static Result calculateOilChangeAlert(
        Integer horasActual,
        Integer hourStamp,
        Integer hourRange,
        String oilType
    ) {
        // Validar datos
        if (horasActual == null || hourStamp == null || hourRange == null || hourRange <= 0) {
            return new Result("VERDE", 0.0, 0, "Datos incompletos para cálculo de alerta");
        }

        // Calcular métricas
        Integer horasSinceChange = horasActual - hourStamp;
        Integer nextChangeHours = hourStamp + hourRange;
        Integer horasRemaining = nextChangeHours - horasActual;
        Double percentageUsed = (double) (horasSinceChange * 100) / hourRange;

        String oilLabel = "MOTOR".equals(oilType) ? "ACEITE MOTOR" : "ACEITE HIDRÁULICO";
        String machinePrefix = "🛢️ ";

        // Determinar color y descripción
        if (percentageUsed >= 100) {
            return new Result(
                "ROJO",
                percentageUsed,
                horasRemaining,
                machinePrefix + "CAMBIO DE " + oilLabel + " VENCIDO (" + Math.abs(horasRemaining) + " horas atrás)"
            );
        } else if (percentageUsed >= 90) {
            return new Result(
                "ROJO",
                percentageUsed,
                horasRemaining,
                machinePrefix + "CAMBIO DE " + oilLabel + " CRÍTICO (" + horasRemaining + " horas restantes)"
            );
        } else if (percentageUsed >= 70) {
            return new Result(
                "AMARILLO",
                percentageUsed,
                horasRemaining,
                machinePrefix + "PRÓXIMO CAMBIO DE " + oilLabel + " (" + horasRemaining + " horas restantes)"
            );
        } else {
            return new Result(
                "VERDE",
                percentageUsed,
                horasRemaining,
                oilLabel + " en buen estado"
            );
        }
    }
}
