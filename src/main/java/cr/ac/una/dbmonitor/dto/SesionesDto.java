package cr.ac.una.dbmonitor.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record SesionesDto(List<Sesion> sesiones, List<Bloqueo> bloqueos,
                          Integer activas, Integer inactivas) {
    public SesionesDto {
        sesiones = sesiones == null ? null : List.copyOf(sesiones);
        bloqueos = bloqueos == null ? null : List.copyOf(bloqueos);
    }

    public record Sesion(Integer sid, Integer serial, String usuario, String estado, String usuarioSistema,
                         String maquina, String programa, LocalDateTime inicioSesion,
                         BigDecimal segundosDesdeUltimaLlamada, String sqlId, String evento,
                         String estadoEspera, Integer bloqueadaPor) {}

    public record Bloqueo(Integer sidBloqueada, Integer serialBloqueada, String usuarioBloqueado,
                          String evento, BigDecimal segundosEspera, String sqlBloqueada,
                          Integer sidBloqueadora, String usuarioBloqueador, String programaBloqueador,
                          String estadoBloqueador) {}
}
