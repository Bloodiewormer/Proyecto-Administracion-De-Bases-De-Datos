package cr.ac.una.dbmonitor.dto;

import java.math.BigDecimal;
import java.util.List;

public record MemoriaInstanciaDto(List<Componente> componentes, BigDecimal totalSgaBytes) {
    public MemoriaInstanciaDto {
        componentes = List.copyOf(componentes);
    }

    public record Componente(String nombre, BigDecimal bytes, Integer conId) {}
}
