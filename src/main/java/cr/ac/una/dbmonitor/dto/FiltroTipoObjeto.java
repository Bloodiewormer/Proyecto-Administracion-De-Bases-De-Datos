package cr.ac.una.dbmonitor.dto;

public enum FiltroTipoObjeto {
    TODOS("Todos los objetos"),
    TABLA("Tablas"),
    INDICE("Índices"),
    LOB("Segmentos LOB");

    private final String etiqueta;

    FiltroTipoObjeto(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    public static FiltroTipoObjeto desde(String valor) {
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
