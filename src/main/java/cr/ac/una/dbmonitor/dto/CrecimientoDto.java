package cr.ac.una.dbmonitor.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record CrecimientoDto(List<Medicion> mediciones, List<HistoricoAwr> historicoAwr) {
    public CrecimientoDto {
        mediciones = mediciones == null ? List.of() : List.copyOf(mediciones);
        historicoAwr = historicoAwr == null ? List.of() : List.copyOf(historicoAwr);
    }

    public record Medicion(LocalDateTime fecha, BigDecimal tamanoTotalBytes,
                           BigDecimal usadoBytes, BigDecimal libreBytes,
                           BigDecimal variacionBytes, BigDecimal variacionPorcentual) {}

    public record HistoricoAwr(LocalDateTime fecha, String tablespace,
                               BigDecimal usadoBytes, BigDecimal tamanoBytes) {}
}
