package cr.ac.una.dbmonitor.dto;

import java.math.BigDecimal;

public record ArchivoDatosDto(Integer fileId, String nombreArchivo, String tablespace,
                              BigDecimal bytes, BigDecimal maxBytes, String autoextensible,
                              BigDecimal incremento, String estado, String tipo) {
}
