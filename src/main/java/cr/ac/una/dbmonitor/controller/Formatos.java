package cr.ac.una.dbmonitor.controller;

import cr.ac.una.dbmonitor.dto.NivelRespaldo;
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
        var abs = bytes.abs();
        if (abs.compareTo(GIB) >= 0) {
            return bytes.divide(GIB, 2, RoundingMode.HALF_UP).toPlainString() + " GiB";
        }
        if (abs.compareTo(MIB) >= 0) {
            return bytes.divide(MIB, 2, RoundingMode.HALF_UP).toPlainString() + " MiB";
        }
        if (abs.compareTo(KIB) >= 0) {
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

    public String porcentaje(BigDecimal valor) {
        if (valor == null) {
            return NO_DISPONIBLE;
        }
        return valor.setScale(2, RoundingMode.HALF_UP).toPlainString() + " %";
    }

    public String entero(Integer valor) {
        return valor == null ? NO_DISPONIBLE : valor.toString();
    }

    public String tipoRespaldo(String tipo) {
        if (tipo == null) {
            return NO_DISPONIBLE;
        }
        return switch (tipo) {
            case "DB FULL" -> "Base de datos completa";
            case "DB INCR" -> "Base de datos incremental";
            case "DATAFILE FULL" -> "Archivos de datos completos";
            case "DATAFILE INCR" -> "Archivos de datos incrementales";
            case "ARCHIVELOG" -> "Registros archivados (archivelog)";
            case "CONTROLFILE" -> "Archivo de control";
            case "SPFILE" -> "Archivo de parámetros (SPFILE)";
            case "RECVR AREA" -> "Área de recuperación";
            default -> tipo;
        };
    }

    public String estadoRespaldo(String estado) {
        if (estado == null) {
            return NO_DISPONIBLE;
        }
        return switch (estado) {
            case "COMPLETED" -> "Completado";
            case "COMPLETED WITH WARNINGS" -> "Completado con advertencias";
            case "COMPLETED WITH ERRORS" -> "Completado con errores";
            case "FAILED" -> "Fallido";
            case "RUNNING" -> "En ejecución";
            case "RUNNING WITH WARNINGS" -> "En ejecución con advertencias";
            case "RUNNING WITH ERRORS" -> "En ejecución con errores";
            default -> estado;
        };
    }

    public String nivelRespaldo(NivelRespaldo nivel) {
        if (nivel == null) {
            return NO_DISPONIBLE;
        }
        return switch (nivel) {
            case ADECUADO -> "Adecuado";
            case ADVERTENCIA -> "Advertencia";
            case RIESGO -> "Riesgo";
        };
    }

    public String siNo(boolean valor) {
        return valor ? "Sí" : "No";
    }

    public String estadoCuenta(String estado) {
        if (estado == null) {
            return NO_DISPONIBLE;
        }
        return switch (estado) {
            case "OPEN" -> "Abierta";
            case "OPEN & IN ROLLOVER" -> "Abierta (cambio gradual de contraseña)";
            case "LOCKED" -> "Bloqueada";
            case "LOCKED(TIMED)" -> "Bloqueada temporalmente por intentos fallidos";
            case "EXPIRED" -> "Contraseña expirada";
            case "EXPIRED(GRACE)" -> "Contraseña en periodo de gracia";
            case "EXPIRED & LOCKED" -> "Contraseña expirada y cuenta bloqueada";
            case "EXPIRED & LOCKED(TIMED)" -> "Contraseña expirada y bloqueo temporal";
            case "EXPIRED(GRACE) & LOCKED" -> "Periodo de gracia y cuenta bloqueada";
            case "EXPIRED(GRACE) & LOCKED(TIMED)" -> "Periodo de gracia y bloqueo temporal";
            default -> estado;
        };
    }

    public String claseEstadoCuenta(String estado) {
        if (estado == null) {
            return "text-bg-secondary";
        }
        if (estado.startsWith("OPEN")) {
            return "text-bg-success";
        }
        return estado.contains("LOCKED") ? "text-bg-danger" : "text-bg-warning";
    }

    public String autenticacionUsuario(String tipo) {
        if (tipo == null) {
            return NO_DISPONIBLE;
        }
        return switch (tipo) {
            case "PASSWORD" -> "Contraseña";
            case "EXTERNAL" -> "Externa (sistema operativo)";
            case "GLOBAL" -> "Global (servicio de directorio)";
            case "NONE" -> "Sin inicio de sesión (solo esquema)";
            default -> tipo;
        };
    }

    public String autenticacionRol(String tipo) {
        if (tipo == null) {
            return NO_DISPONIBLE;
        }
        return switch (tipo) {
            case "NONE" -> "No requiere contraseña";
            case "PASSWORD" -> "Protegido con contraseña";
            case "EXTERNAL" -> "Externa (sistema operativo)";
            case "GLOBAL" -> "Global (servicio de directorio)";
            case "APPLICATION" -> "Activado por un paquete de aplicación";
            default -> tipo;
        };
    }

    public String alcanceCuenta(boolean comun) {
        return comun ? "Común (todos los contenedores)" : "Local (este contenedor)";
    }

    public String tipoBeneficiario(String tipo) {
        if (tipo == null) {
            return NO_DISPONIBLE;
        }
        return switch (tipo) {
            case "USUARIO" -> "Usuario";
            case "ROL" -> "Rol";
            default -> "Otro (por ejemplo PUBLIC)";
        };
    }
}
