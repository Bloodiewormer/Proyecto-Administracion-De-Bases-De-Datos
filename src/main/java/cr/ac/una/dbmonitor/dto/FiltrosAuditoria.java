package cr.ac.una.dbmonitor.dto;

import java.util.Locale;

public record FiltrosAuditoria(VistaAuditoria vista, FiltroOrigen origen, String busqueda, int limite,
                               String beneficiario, OrigenPrivilegio origenPrivilegio) {

    public boolean filtraPorOrigen() {
        return origen.valorOracle() != null;
    }

    public boolean filtraPorTexto() {
        return busqueda != null;
    }

    /** Patrón para LIKE ... ESCAPE '\': los comodines escritos por el usuario se tratan como texto literal. */
    public String patronBusqueda() {
        if (busqueda == null) {
            return null;
        }
        var literal = busqueda.toUpperCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + literal + "%";
    }
}
