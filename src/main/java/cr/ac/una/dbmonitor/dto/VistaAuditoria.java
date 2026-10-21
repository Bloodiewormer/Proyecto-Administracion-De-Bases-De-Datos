package cr.ac.una.dbmonitor.dto;

public enum VistaAuditoria {
    USUARIOS("Usuarios", "Buscar usuario"),
    ROLES("Roles", "Buscar rol o beneficiario"),
    PRIVILEGIOS("Privilegios", "Buscar privilegio, propietario u objeto"),
    OBJETOS("Objetos con problemas", "Buscar propietario u objeto");

    private final String etiqueta;
    private final String etiquetaBusqueda;

    VistaAuditoria(String etiqueta, String etiquetaBusqueda) {
        this.etiqueta = etiqueta;
        this.etiquetaBusqueda = etiquetaBusqueda;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    public String getEtiquetaBusqueda() {
        return etiquetaBusqueda;
    }

    public static VistaAuditoria desde(String valor) {
        if (valor == null) {
            return USUARIOS;
        }
        for (var vista : values()) {
            if (vista.name().equalsIgnoreCase(valor.trim())) {
                return vista;
            }
        }
        return USUARIOS;
    }
}
