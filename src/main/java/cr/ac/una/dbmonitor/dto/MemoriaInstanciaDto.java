package cr.ac.una.dbmonitor.dto;

import java.math.BigDecimal;
import java.util.List;

public record MemoriaInstanciaDto(List<Componente> componentes, BigDecimal totalSgaBytes, Uso uso) {
    public MemoriaInstanciaDto {
        componentes = List.copyOf(componentes);
    }

    public record Componente(String nombre, BigDecimal bytes, Integer conId) {}

    public record Uso(BigDecimal sgaLibreBytes, BigDecimal sgaUtilizadaBytes,
                      BigDecimal pgaAsignadaBytes, BigDecimal pgaUtilizadaBytes) {}
}
