package cr.ac.una.dbmonitor.dto;

import java.util.Optional;

/**
 * Tipos de objeto que admiten recompilación individual y la sentencia ALTER ... COMPILE de cada uno.
 * Las palabras clave son fijas; solo el propietario y el nombre del objeto provienen de la base de datos.
 */
public enum TipoCompilacion {
    VIEW("VIEW", "ALTER VIEW", "COMPILE"),
    MATERIALIZED_VIEW("MATERIALIZED VIEW", "ALTER MATERIALIZED VIEW", "COMPILE"),
    PROCEDURE("PROCEDURE", "ALTER PROCEDURE", "COMPILE"),
    FUNCTION("FUNCTION", "ALTER FUNCTION", "COMPILE"),
    PACKAGE("PACKAGE", "ALTER PACKAGE", "COMPILE"),
    PACKAGE_BODY("PACKAGE BODY", "ALTER PACKAGE", "COMPILE BODY"),
    TRIGGER("TRIGGER", "ALTER TRIGGER", "COMPILE"),
    TYPE("TYPE", "ALTER TYPE", "COMPILE"),
    TYPE_BODY("TYPE BODY", "ALTER TYPE", "COMPILE BODY"),
    SYNONYM("SYNONYM", "ALTER SYNONYM", "COMPILE");

    private final String tipoOracle;
    private final String sentencia;
    private final String clausula;

    TipoCompilacion(String tipoOracle, String sentencia, String clausula) {
        this.tipoOracle = tipoOracle;
        this.sentencia = sentencia;
        this.clausula = clausula;
    }

    public String tipoOracle() {
        return tipoOracle;
    }

    public String sentencia() {
        return sentencia;
    }

    public String clausula() {
        return clausula;
    }

    public static Optional<TipoCompilacion> desde(String tipoOracle) {
        if (tipoOracle == null) {
            return Optional.empty();
        }
        for (var tipo : values()) {
            if (tipo.tipoOracle.equals(tipoOracle)) {
                return Optional.of(tipo);
            }
        }
        return Optional.empty();
    }
}
