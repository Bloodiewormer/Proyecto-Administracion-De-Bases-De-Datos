package cr.ac.una.dbmonitor.dto;

import java.math.BigDecimal;

public record TablespaceDto(String nombre, String contenido, String estado,
                            String gestionExtensiones, String gestionSegmentos,
                            BigDecimal bytesTotales, BigDecimal bytesUsados,
                            BigDecimal bytesLibres, BigDecimal bytesMaximos,
                            BigDecimal porcentajeUso) {
}
