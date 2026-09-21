package cr.ac.una.dbmonitor.dto;

import java.math.BigDecimal;

public record ObjetoAlmacenamientoDto(String propietario, String nombre, String particion,
                                      String tipo, String tablespace, BigDecimal bytes,
                                      BigDecimal bloques, BigDecimal extensiones) {
}
