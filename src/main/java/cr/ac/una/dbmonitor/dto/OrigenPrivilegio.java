package cr.ac.una.dbmonitor.dto;

public enum OrigenPrivilegio {
    TODOS("Directos y mediante roles"),
    DIRECTOS("Solo asignados directamente"),
    ROLES("Solo heredados mediante roles");

    private final String etiqueta;

    OrigenPrivilegio(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    public static OrigenPrivilegio desde(String valor) {
        if (valor == null) {
            return TODOS;
        }
        for (var origen : values()) {
            if (origen.name().equalsIgnoreCase(valor.trim())) {
                return origen;
            }
        }
        return TODOS;
    }
}
