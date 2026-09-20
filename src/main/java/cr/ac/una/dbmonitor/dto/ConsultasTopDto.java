package cr.ac.una.dbmonitor.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record ConsultasTopDto(Resumen resumen, List<Consulta> consultas) {
    public ConsultasTopDto {
        consultas = consultas == null ? null : List.copyOf(consultas);
    }

    public record Consulta(String sqlId, String texto, BigDecimal ejecuciones,
                           BigDecimal tiempoTotalMicros, BigDecimal tiempoPromedioMicros,
                           BigDecimal cpuMicros, BigDecimal lecturasMemoria, BigDecimal lecturasDisco,
                           BigDecimal filasProcesadas, LocalDateTime ultimaActividad) {}

    public record Resumen(BigDecimal ejecuciones, BigDecimal tiempoTotalMicros,
                          BigDecimal tiempoPromedioMicros, BigDecimal cpuMicros,
                          BigDecimal lecturasMemoria, BigDecimal lecturasDisco) {}
}
