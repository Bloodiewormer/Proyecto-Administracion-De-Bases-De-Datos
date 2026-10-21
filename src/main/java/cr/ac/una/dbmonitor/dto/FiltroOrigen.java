package cr.ac.una.dbmonitor.dto;

/**
 * Filtra cuentas, roles y objetos según la columna ORACLE_MAINTAINED del diccionario:
 * 'Y' identifica lo creado y mantenido por los scripts de Oracle, 'N' lo creado por la organización.
 */
public enum FiltroOrigen {
    TODOS("Todos", null),
    ORGANIZACION("Creados por la organización", "N"),
    ORACLE("Mantenidos por Oracle", "Y");

    private final String etiqueta;
    private final String valorOracle;

    FiltroOrigen(String etiqueta, String valorOracle) {
        this.etiqueta = etiqueta;
        this.valorOracle = valorOracle;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    public String valorOracle() {
        return valorOracle;
    }

    public boolean incluye(boolean mantenidoPorOracle) {
        return valorOracle == null || mantenidoPorOracle == "Y".equals(valorOracle);
    }

    public static FiltroOrigen desde(String valor) {
        if (valor == null) {
            return TODOS;
        }
        for (var filtro : values()) {
            if (filtro.name().equalsIgnoreCase(valor.trim())) {
                return filtro;
            }
        }
        return TODOS;
    }
}
