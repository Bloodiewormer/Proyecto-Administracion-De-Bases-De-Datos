package cr.ac.una.dbmonitor.controller;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;

@Component
public class Formatos {
    private static final String NO_DISPONIBLE = "No disponible";
    private static final BigDecimal GIB = BigDecimal.valueOf(1073741824);
    private static final BigDecimal MIB = BigDecimal.valueOf(1048576);
    private static final BigDecimal KIB = BigDecimal.valueOf(1024);
    private static final BigDecimal MICROSEGUNDOS_POR_SEGUNDO = BigDecimal.valueOf(1000000);
    private static final BigDecimal MICROSEGUNDOS_POR_MILISEGUNDO = BigDecimal.valueOf(1000);
    private static final BigDecimal CENTISEGUNDOS_POR_SEGUNDO = BigDecimal.valueOf(100);

    public String memoria(BigDecimal bytes) {
        if (bytes == null) {
            return NO_DISPONIBLE;
        }
        if (bytes.compareTo(GIB) >= 0) {
            return bytes.divide(GIB, 2, RoundingMode.HALF_UP).toPlainString() + " GiB";
        }
        if (bytes.compareTo(MIB) >= 0) {
            return bytes.divide(MIB, 2, RoundingMode.HALF_UP).toPlainString() + " MiB";
        }
        if (bytes.compareTo(KIB) >= 0) {
            return bytes.divide(KIB, 2, RoundingMode.HALF_UP).toPlainString() + " KiB";
        }
        return bytes.toPlainString() + " bytes";
    }

    public String tiempo(BigDecimal microsegundos) {
        if (microsegundos == null) {
            return NO_DISPONIBLE;
        }
        if (microsegundos.compareTo(MICROSEGUNDOS_POR_SEGUNDO) >= 0) {
            return microsegundos.divide(MICROSEGUNDOS_POR_SEGUNDO, 2, RoundingMode.HALF_UP).toPlainString() + " s";
        }
        if (microsegundos.compareTo(MICROSEGUNDOS_POR_MILISEGUNDO) >= 0) {
            return microsegundos.divide(MICROSEGUNDOS_POR_MILISEGUNDO, 2, RoundingMode.HALF_UP).toPlainString() + " ms";
        }
        return microsegundos.setScale(0, RoundingMode.HALF_UP).toPlainString() + " µs";
    }

    public String cpu(BigDecimal centisegundos) {
        if (centisegundos == null) {
            return NO_DISPONIBLE;
        }
        return centisegundos.divide(CENTISEGUNDOS_POR_SEGUNDO, 2, RoundingMode.HALF_UP).toPlainString() + " s";
    }

    public String segundos(BigDecimal segundos) {
        if (segundos == null) {
            return NO_DISPONIBLE;
        }
        return duracion(Duration.ofSeconds(segundos.setScale(0, RoundingMode.HALF_UP).longValue()));
    }

    public String duracion(Duration duracion) {
        if (duracion == null || duracion.isNegative()) {
            return NO_DISPONIBLE;
        }
        return duracion.toDays() + " d " + duracion.toHoursPart() + " h "
                + duracion.toMinutesPart() + " min " + duracion.toSecondsPart() + " s";
    }

    public String numero(BigDecimal valor) {
        return valor == null ? NO_DISPONIBLE : valor.toPlainString();
    }

    public String texto(String valor) {
        return valor == null || valor.isBlank() ? NO_DISPONIBLE : valor;
    }

    public String tipoProceso(String tipo) {
        if (tipo == null) {
            return NO_DISPONIBLE;
        }
        return switch (tipo) {
            case "BACKGROUND" -> "Interno de Oracle";
            case "USER" -> "Usuario";
            default -> tipo;
        };
    }
}
