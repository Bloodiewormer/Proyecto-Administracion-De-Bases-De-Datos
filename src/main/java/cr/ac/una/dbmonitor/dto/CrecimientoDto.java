package cr.ac.una.dbmonitor.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record CrecimientoDto(List<Medicion> mediciones, List<HistoricoAwr> historicoAwr, Origen origen) {
    public CrecimientoDto(List<Medicion> mediciones, List<HistoricoAwr> historicoAwr) {
        this(mediciones, historicoAwr, null);
    }
    public CrecimientoDto {
        mediciones = mediciones == null ? List.of() : List.copyOf(mediciones);
        historicoAwr = historicoAwr == null ? List.of() : List.copyOf(historicoAwr);
    }

    public record Medicion(LocalDateTime fecha, BigDecimal tamanoTotalBytes,
                           BigDecimal usadoBytes, BigDecimal libreBytes,
                           BigDecimal variacionBytes, BigDecimal variacionPorcentual) {}

    public record HistoricoAwr(LocalDateTime fecha, String tablespace,
                               BigDecimal usadoBytes, BigDecimal tamanoBytes) {}

    /** Identidad obtenida de Oracle, nunca del formulario. */
    public record Origen(BigDecimal dbid, BigDecimal contenedorDbid, int conId,
                         String instancia, String servicio, String contenedor) {
        public boolean valida() {
            return dbid != null && dbid.signum() > 0 && contenedorDbid != null
                    && contenedorDbid.signum() > 0 && conId >= 0
                    && instancia != null && !instancia.isBlank()
                    && contenedor != null && !contenedor.isBlank();
        }

        // Otro servicio o instancia RAC puede consultar el mismo almacenamiento.
        public boolean mismaBase(Origen otro) {
            return valida() && otro != null && otro.valida() && dbid.compareTo(otro.dbid) == 0
                    && contenedorDbid.compareTo(otro.contenedorDbid) == 0 && conId == otro.conId;
        }
    }

    public record Registro(BigDecimal id, LocalDateTime fecha, Origen origen,
                           BigDecimal tamanoTotalBytes, BigDecimal usadoBytes, BigDecimal libreBytes) {
        public boolean valido() {
            return id != null && id.signum() > 0 && fecha != null && origen != null && origen.valida()
                    && tamanoTotalBytes != null && tamanoTotalBytes.signum() >= 0
                    && usadoBytes != null && usadoBytes.signum() >= 0
                    && libreBytes != null && libreBytes.signum() >= 0
                    && usadoBytes.add(libreBytes).compareTo(tamanoTotalBytes) == 0;
        }
    }
}
