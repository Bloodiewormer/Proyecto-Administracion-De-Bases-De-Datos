package cr.ac.una.dbmonitor.dto;

import java.math.BigDecimal;

public record ResumenAlmacenamientoDto(BigDecimal tamanoTotalBytes, BigDecimal usadoBytes,
                                       BigDecimal libreBytes, BigDecimal porcentajeUso,
                                       BigDecimal bytesDatafiles, BigDecimal bytesTempfiles,
                                       BigDecimal bytesRedoLogs, Integer cantidadTablespaces,
                                       Integer cantidadArchivos) {
}
