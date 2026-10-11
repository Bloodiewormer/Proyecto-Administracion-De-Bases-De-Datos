package cr.ac.una.dbmonitor.service;

import cr.ac.una.dbmonitor.dto.NivelRespaldo;
import cr.ac.una.dbmonitor.dto.RespaldoDto;
import cr.ac.una.dbmonitor.repository.RespaldoRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
public class RespaldoService {
    private static final int CANTIDAD_MINIMA = 5;
    private static final int CANTIDAD_MAXIMA = 50;
    private static final int CANTIDAD_POR_DEFECTO = 10;
    private static final long HORAS_ADECUADO = 24;
    private static final long HORAS_ADVERTENCIA = 48;
    private static final String ESTADO_FALLIDO = "FAILED";
    private static final String MODO_SIN_ARCHIVADO = "NOARCHIVELOG";

    private final RespaldoRepository repository;

    public RespaldoService(RespaldoRepository repository) {
        this.repository = repository;
    }

    public RespaldoDto obtenerRespaldos(Integer cantidad) {
        var datos = repository.consultar(limitar(cantidad));
        return datos.conInterpretacion(interpretar(datos, LocalDateTime.now()));
    }

    private int limitar(Integer cantidad) {
        return cantidad == null ? CANTIDAD_POR_DEFECTO : Math.clamp(cantidad, CANTIDAD_MINIMA, CANTIDAD_MAXIMA);
    }

    RespaldoDto.Interpretacion interpretar(RespaldoDto datos, LocalDateTime ahora) {
        var historial = datos.historial();
        if (historial == null) {
            return null;
        }
        if (historial.isEmpty()) {
            return sinHistorial(datos.contenedor());
        }
        var ultimo = datos.ultimoExitoso();
        if (ultimo == null) {
            return new RespaldoDto.Interpretacion(NivelRespaldo.RIESGO,
                    "Hay respaldos registrados, pero ninguno finalizó correctamente.", null);
        }
        var fecha = ultimo.fin() != null ? ultimo.fin() : ultimo.inicio();
        if (fecha == null) {
            return new RespaldoDto.Interpretacion(NivelRespaldo.ADVERTENCIA,
                    "No se pudo determinar la fecha del último respaldo exitoso.", null);
        }
        var horas = Math.max(0, Duration.between(fecha, ahora).toHours());
        var nivel = nivelPorAntiguedad(horas);
        var mensaje = new StringBuilder("El último respaldo exitoso fue hace " + tiempo(horas) + ".");
        if (fallo(historial.get(0))) {
            nivel = mayor(nivel, NivelRespaldo.ADVERTENCIA);
            mensaje.append(" El intento más reciente falló.");
        }
        if (MODO_SIN_ARCHIVADO.equals(datos.modoLog())) {
            nivel = mayor(nivel, NivelRespaldo.ADVERTENCIA);
            mensaje.append(" La base está en modo NOARCHIVELOG: no permite recuperar hasta un punto en el tiempo, "
                    + "solo restaurar el último respaldo.");
        }
        return new RespaldoDto.Interpretacion(nivel, mensaje.toString(), horas);
    }

    private RespaldoDto.Interpretacion sinHistorial(RespaldoDto.Contenedor contenedor) {
        if (contenedor != null && contenedor.esPdb()) {
            return new RespaldoDto.Interpretacion(NivelRespaldo.ADVERTENCIA,
                    "Está conectado a la base «" + contenedor.nombre() + "» (PDB) y no hay respaldos registrados. "
                            + "Los respaldos hechos con RMAN conectado a la base raíz no aparecen aquí: "
                            + "genérelos con RMAN conectado a este mismo servicio.", null);
        }
        return new RespaldoDto.Interpretacion(NivelRespaldo.RIESGO,
                "No hay respaldos registrados en RMAN. Sin una copia de seguridad no es posible "
                        + "recuperar la base ante un fallo.", null);
    }

    private NivelRespaldo nivelPorAntiguedad(long horas) {
        if (horas <= HORAS_ADECUADO) {
            return NivelRespaldo.ADECUADO;
        }
        return horas <= HORAS_ADVERTENCIA ? NivelRespaldo.ADVERTENCIA : NivelRespaldo.RIESGO;
    }

    private NivelRespaldo mayor(NivelRespaldo a, NivelRespaldo b) {
        return a.compareTo(b) >= 0 ? a : b;
    }

    private boolean fallo(RespaldoDto.Respaldo respaldo) {
        return respaldo.estadoOracle() != null && respaldo.estadoOracle().startsWith(ESTADO_FALLIDO);
    }

    private String tiempo(long horas) {
        if (horas < 1) {
            return "menos de una hora";
        }
        if (horas == 1) {
            return "1 hora";
        }
        return horas < 48 ? horas + " horas" : (horas / 24) + " días";
    }
}
