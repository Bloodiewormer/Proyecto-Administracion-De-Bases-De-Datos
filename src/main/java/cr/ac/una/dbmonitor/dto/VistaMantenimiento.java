package cr.ac.una.dbmonitor.dto;

public enum VistaMantenimiento {
    ESTADISTICAS("Estadísticas"),
    RECOMPILACION("Recompilación de objetos");

    private final String etiqueta;

    VistaMantenimiento(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    public static VistaMantenimiento desde(String valor) {
        if (valor == null) {
            return ESTADISTICAS;
        }
        for (var vista : values()) {
            if (vista.name().equalsIgnoreCase(valor.trim())) {
                return vista;
            }
        }
        return ESTADISTICAS;
    }
}
