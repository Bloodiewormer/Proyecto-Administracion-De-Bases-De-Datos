package cr.ac.una.dbmonitor.dto;

public enum OrdenConsultas {
    TIEMPO_TOTAL("Tiempo total de ejecución"),
    TIEMPO_PROMEDIO("Tiempo promedio por ejecución"),
    CPU("Tiempo de CPU"),
    LECTURAS_DISCO("Lecturas físicas en disco"),
    LECTURAS_MEMORIA("Lecturas lógicas en memoria"),
    EJECUCIONES("Cantidad de ejecuciones");

    private final String etiqueta;

    OrdenConsultas(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    public static OrdenConsultas desde(String valor) {
        if (valor == null) {
            return TIEMPO_TOTAL;
        }
        for (var orden : values()) {
            if (orden.name().equalsIgnoreCase(valor.trim())) {
                return orden;
            }
        }
        return TIEMPO_TOTAL;
    }
}
