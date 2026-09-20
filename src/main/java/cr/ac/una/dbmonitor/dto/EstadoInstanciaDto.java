package cr.ac.una.dbmonitor.dto;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

public record EstadoInstanciaDto(Instancia instancia, Duration uptime, MemoriaInstanciaDto memoria,
                                 BaseDatosDto baseDatos, List<String> avisos) {
    public EstadoInstanciaDto {
        avisos = List.copyOf(avisos);
    }

    public record Instancia(String host, String nombre, String version, String edicion, String tipoDespliegue,
                            String estado, LocalDateTime inicio, LocalDateTime fechaConsulta) {}
}
