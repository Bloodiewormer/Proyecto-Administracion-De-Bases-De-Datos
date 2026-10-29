package cr.ac.una.dbmonitor.dto;

import java.util.List;

/** Filas devueltas por una consulta limitada y total de coincidencias antes de aplicar el límite. */
public record Listado<T>(List<T> filas, int total) {
    public Listado {
        filas = List.copyOf(filas);
    }

    public boolean truncado() {
        return total > filas.size();
    }
}
