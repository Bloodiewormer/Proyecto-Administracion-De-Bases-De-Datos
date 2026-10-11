package cr.ac.una.dbmonitor.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record RespaldoDto(Respaldo ultimoExitoso, List<Respaldo> historial, String modoLog, Contenedor contenedor,
                          Interpretacion interpretacion, int cantidad, List<String> avisos) {
    public RespaldoDto {
        historial = historial == null ? null : List.copyOf(historial);
        avisos = List.copyOf(avisos);
    }

    public RespaldoDto conInterpretacion(Interpretacion nueva) {
        return new RespaldoDto(ultimoExitoso, historial, modoLog, contenedor, nueva, cantidad, avisos);
    }

    public record Respaldo(String sessionKey, LocalDateTime inicio, LocalDateTime fin, String tipoOracle,
                           String estadoOracle, BigDecimal bytesGenerados, BigDecimal duracionSegundos,
                           String dispositivo) {}

    public record Contenedor(String nombre, boolean esPdb) {}

    public record Interpretacion(NivelRespaldo nivel, String mensaje, Long horasDesdeUltimoExitoso) {}
}
