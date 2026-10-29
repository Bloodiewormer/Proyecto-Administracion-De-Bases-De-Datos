package cr.ac.una.dbmonitor.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record MantenimientoDto(VistaMantenimiento vista, boolean todasLasTablas, int limite,
                               AuditoriaDto.Contexto contexto, Resumen resumen,
                               Listado<TablaEstadisticas> tablas, List<String> esquemas,
                               Listado<ObjetoInvalido> invalidos, List<EsquemaConInvalidos> esquemasConInvalidos,
                               Listado<AuditoriaDto.ErrorCompilacion> errores, List<String> avisos) {
    public MantenimientoDto {
        esquemas = esquemas == null ? null : List.copyOf(esquemas);
        esquemasConInvalidos = esquemasConInvalidos == null ? null : List.copyOf(esquemasConInvalidos);
        avisos = List.copyOf(avisos);
    }

    /** Totales calculados solo sobre los esquemas creados por la organización. */
    public record Resumen(Integer tablasDesactualizadas, Integer tablasSinEstadisticas, Integer objetosInvalidos,
                          Integer erroresCompilacion) {}

    public record TablaEstadisticas(String propietario, String tabla, BigDecimal filas, LocalDateTime ultimoAnalisis,
                                    boolean desactualizadas, String bloqueo) {
        public boolean sinEstadisticas() {
            return ultimoAnalisis == null;
        }

        public boolean bloqueadas() {
            return bloqueo != null;
        }
    }

    public record ObjetoInvalido(String propietario, String nombre, String tipo, LocalDateTime ultimaModificacion,
                                 int errores, boolean recompilable) {}

    public record EsquemaConInvalidos(String esquema, int invalidos) {}
}
