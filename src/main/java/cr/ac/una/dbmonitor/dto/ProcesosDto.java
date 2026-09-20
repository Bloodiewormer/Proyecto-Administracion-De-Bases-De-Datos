package cr.ac.una.dbmonitor.dto;

import java.math.BigDecimal;
import java.util.List;

public record ProcesosDto(List<ProcesoMemoria> memoria, List<SesionCpu> cpu) {
    public ProcesosDto {
        memoria = memoria == null ? null : List.copyOf(memoria);
        cpu = cpu == null ? null : List.copyOf(cpu);
    }

    public record ProcesoMemoria(Integer pid, String spid, String nombre, String programa, String tipo,
                                 BigDecimal pgaAsignadaBytes, BigDecimal pgaUsadaBytes,
                                 BigDecimal pgaMaximaBytes, Integer sid, Integer serial, String usuario,
                                 String estado, String maquina) {}

    public record SesionCpu(Integer sid, Integer serial, String usuario, String programa,
                            String estado, String tipo, BigDecimal cpuCentisegundos) {}
}
