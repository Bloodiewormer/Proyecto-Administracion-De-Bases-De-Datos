package cr.ac.una.dbmonitor.dto;

import java.util.List;

public record RendimientoDto(ConsultasTopDto consultas, SesionesDto sesiones, ProcesosDto procesos,
                             OrdenConsultas orden, int top, List<String> avisos) {
    public RendimientoDto {
        avisos = List.copyOf(avisos);
    }
}
