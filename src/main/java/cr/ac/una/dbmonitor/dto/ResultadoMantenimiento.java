package cr.ac.una.dbmonitor.dto;

import java.io.Serializable;
import java.time.LocalDateTime;

/** Confirmación de una operación de mantenimiento; viaja como atributo flash tras la redirección. */
public record ResultadoMantenimiento(Nivel nivel, String operacion, String objetivo, String detalle,
                                     long milisegundos, LocalDateTime momento) implements Serializable {
    public enum Nivel {
        EXITO,
        ADVERTENCIA,
        ERROR
    }
}
