package cr.ac.una.dbmonitor.service;

import cr.ac.una.dbmonitor.dto.MantenimientoDto;
import cr.ac.una.dbmonitor.dto.ResultadoMantenimiento;
import cr.ac.una.dbmonitor.dto.ResultadoMantenimiento.Nivel;
import cr.ac.una.dbmonitor.dto.TipoCompilacion;
import cr.ac.una.dbmonitor.dto.VistaMantenimiento;
import cr.ac.una.dbmonitor.repository.MantenimientoRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class MantenimientoService {
    private static final int LIMITE_MINIMO = 10;
    private static final int LIMITE_MAXIMO = 500;
    private static final int LIMITE_POR_DEFECTO = 200;
    private static final int LONGITUD_MAXIMA_NOMBRE = 128;
    private static final String VALIDO = "VALID";
    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private static final String OPERACION_TABLA = "Recalcular estadísticas de tabla";
    private static final String OPERACION_ESQUEMA = "Recalcular estadísticas de esquema";
    private static final String OPERACION_OBJETO = "Recompilar objeto";
    private static final String OPERACION_RECOMPILAR_ESQUEMA = "Recompilar objetos inválidos del esquema";

    private final MantenimientoRepository repository;

    public MantenimientoService(MantenimientoRepository repository) {
        this.repository = repository;
    }

    public MantenimientoDto obtenerMantenimiento(VistaMantenimiento vista, boolean todasLasTablas, Integer limite) {
        var filas = limite == null ? LIMITE_POR_DEFECTO : Math.clamp(limite, LIMITE_MINIMO, LIMITE_MAXIMO);
        return repository.consultar(vista, todasLasTablas, filas);
    }

    public ResultadoMantenimiento recalcularEstadisticasTabla(String esquema, String tabla) {
        var inicio = System.nanoTime();
        var objetivo = calificado(esquema, tabla);
        if (!nombreValido(esquema) || !nombreValido(tabla)) {
            return resultado(Nivel.ERROR, OPERACION_TABLA, objetivo, "Seleccione una tabla de la lista.", inicio);
        }
        try {
            if (!repository.esTablaDeOrganizacion(esquema, tabla)) {
                return resultado(Nivel.ERROR, OPERACION_TABLA, objetivo, "La tabla no existe o pertenece a un "
                        + "esquema mantenido por Oracle; la operación no se ejecutó.", inicio);
            }
            repository.recalcularEstadisticasTabla(esquema, tabla);
            var detalle = repository.estadisticasTabla(esquema, tabla)
                    .map(e -> "Estadísticas de la tabla y sus índices recalculadas. Filas registradas: "
                            + (e.filas() == null ? "sin dato" : e.filas())
                            + ". Último análisis según el reloj del servidor de base de datos: "
                            + fecha(e.ultimoAnalisis()) + ".")
                    .orElse("Oracle completó la operación, pero no se pudo leer el resultado en DBA_TAB_STATISTICS.");
            return resultado(Nivel.EXITO, OPERACION_TABLA, objetivo, detalle, inicio);
        } catch (IllegalStateException | DataAccessException ex) {
            return fallo(OPERACION_TABLA, objetivo, ex, inicio);
        }
    }

    public ResultadoMantenimiento recalcularEstadisticasEsquema(String esquema) {
        var inicio = System.nanoTime();
        if (!nombreValido(esquema)) {
            return resultado(Nivel.ERROR, OPERACION_ESQUEMA, "Sin esquema", "Seleccione un esquema de la lista.",
                    inicio);
        }
        try {
            if (!repository.esEsquemaDeOrganizacion(esquema)) {
                return resultado(Nivel.ERROR, OPERACION_ESQUEMA, esquema, "El esquema no existe o es mantenido por "
                        + "Oracle; la operación no se ejecutó.", inicio);
            }
            var desde = repository.momentoActual();
            repository.recalcularEstadisticasEsquema(esquema);
            var tablas = repository.tablasAnalizadas(esquema, desde);
            if (tablas.total() == 0) {
                return resultado(Nivel.ADVERTENCIA, OPERACION_ESQUEMA, esquema,
                        "La operación terminó, pero el esquema no tiene tablas que analizar.", inicio);
            }
            var detalle = "Se actualizaron las estadísticas de " + tablas.analizadas() + " de " + tablas.total()
                    + " tablas del esquema.";
            if (tablas.analizadas() < tablas.total()) {
                return resultado(Nivel.ADVERTENCIA, OPERACION_ESQUEMA, esquema, detalle + " Las demás no se "
                        + "actualizaron; revise si tienen estadísticas bloqueadas.", inicio);
            }
            return resultado(Nivel.EXITO, OPERACION_ESQUEMA, esquema, detalle, inicio);
        } catch (IllegalStateException | DataAccessException ex) {
            return fallo(OPERACION_ESQUEMA, esquema, ex, inicio);
        }
    }

    public ResultadoMantenimiento recompilarObjeto(String esquema, String nombre, String tipoOracle) {
        var inicio = System.nanoTime();
        var objetivo = calificado(esquema, nombre) + (tipoOracle == null ? "" : " (" + tipoOracle + ")");
        var tipo = TipoCompilacion.desde(tipoOracle);
        if (!nombreValido(esquema) || !nombreValido(nombre)) {
            return resultado(Nivel.ERROR, OPERACION_OBJETO, objetivo, "Seleccione un objeto de la lista.", inicio);
        }
        if (tipo.isEmpty()) {
            return resultado(Nivel.ERROR, OPERACION_OBJETO, objetivo, "Este tipo de objeto no admite recompilación "
                    + "individual. Use la recompilación del esquema completo.", inicio);
        }
        try {
            var antes = repository.estadoObjeto(esquema, nombre, tipoOracle);
            if (antes.isEmpty()) {
                return resultado(Nivel.ERROR, OPERACION_OBJETO, objetivo, "El objeto no existe o pertenece a un "
                        + "esquema mantenido por Oracle; la operación no se ejecutó.", inicio);
            }
            repository.recompilarObjeto(tipo.get(), esquema, nombre);
            var despues = repository.estadoObjeto(esquema, nombre, tipoOracle).orElse(antes.get());
            if (VALIDO.equals(despues.estado())) {
                return resultado(Nivel.EXITO, OPERACION_OBJETO, objetivo,
                        "El objeto se recompiló correctamente y su estado ahora es VALID.", inicio);
            }
            var detalle = "Oracle ejecutó la recompilación, pero el objeto sigue en estado " + despues.estado()
                    + " con " + despues.errores() + " error(es) de compilación.";
            if (despues.primerError() != null) {
                detalle += " Primer error: " + despues.primerError().trim();
            }
            return resultado(Nivel.ERROR, OPERACION_OBJETO, objetivo, detalle, inicio);
        } catch (IllegalStateException | DataAccessException ex) {
            return fallo(OPERACION_OBJETO, objetivo, ex, inicio);
        }
    }

    public ResultadoMantenimiento recompilarEsquema(String esquema) {
        var inicio = System.nanoTime();
        if (!nombreValido(esquema)) {
            return resultado(Nivel.ERROR, OPERACION_RECOMPILAR_ESQUEMA, "Sin esquema",
                    "Seleccione un esquema de la lista.", inicio);
        }
        try {
            if (!repository.esEsquemaDeOrganizacion(esquema)) {
                return resultado(Nivel.ERROR, OPERACION_RECOMPILAR_ESQUEMA, esquema, "El esquema no existe o es "
                        + "mantenido por Oracle; la operación no se ejecutó.", inicio);
            }
            var antes = repository.invalidosDelEsquema(esquema);
            if (antes == 0) {
                return resultado(Nivel.EXITO, OPERACION_RECOMPILAR_ESQUEMA, esquema,
                        "El esquema no tenía objetos inválidos; no fue necesario recompilar.", inicio);
            }
            repository.recompilarEsquema(esquema);
            var despues = repository.invalidosDelEsquema(esquema);
            var corregidos = Math.max(0, antes - despues);
            if (despues == 0) {
                return resultado(Nivel.EXITO, OPERACION_RECOMPILAR_ESQUEMA, esquema, "Se recompilaron los " + antes
                        + " objetos inválidos y todos quedaron en estado VALID.", inicio);
            }
            var detalle = "Había " + antes + (antes == 1 ? " objeto inválido: " : " objetos inválidos: ")
                    + corregidos + (corregidos == 1 ? " quedó válido y " : " quedaron válidos y ")
                    + despues + (despues == 1 ? " sigue inválido." : " siguen inválidos.")
                    + " Revise los errores de compilación.";
            return resultado(corregidos > 0 ? Nivel.ADVERTENCIA : Nivel.ERROR, OPERACION_RECOMPILAR_ESQUEMA,
                    esquema, detalle, inicio);
        } catch (IllegalStateException | DataAccessException ex) {
            return fallo(OPERACION_RECOMPILAR_ESQUEMA, esquema, ex, inicio);
        }
    }

    private boolean nombreValido(String nombre) {
        return nombre != null && !nombre.isBlank() && nombre.length() <= LONGITUD_MAXIMA_NOMBRE;
    }

    private String calificado(String esquema, String nombre) {
        return (esquema == null ? "?" : esquema) + "." + (nombre == null ? "?" : nombre);
    }

    private String fecha(LocalDateTime fecha) {
        return fecha == null ? "sin dato" : fecha.format(FORMATO_FECHA);
    }

    private ResultadoMantenimiento fallo(String operacion, String objetivo, RuntimeException error, long inicio) {
        var detalle = error instanceof DataAccessException acceso ? MantenimientoRepository.mensaje(acceso)
                : error.getMessage();
        return resultado(Nivel.ERROR, operacion, objetivo, detalle, inicio);
    }

    private ResultadoMantenimiento resultado(Nivel nivel, String operacion, String objetivo, String detalle,
                                             long inicio) {
        var milisegundos = (System.nanoTime() - inicio) / 1_000_000;
        return new ResultadoMantenimiento(nivel, operacion, objetivo, detalle, milisegundos, LocalDateTime.now());
    }
}
