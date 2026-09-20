package cr.ac.una.dbmonitor.service;

import cr.ac.una.dbmonitor.dto.ConsultasTopDto;
import cr.ac.una.dbmonitor.dto.OrdenConsultas;
import cr.ac.una.dbmonitor.dto.RendimientoDto;
import cr.ac.una.dbmonitor.dto.SesionesDto;
import cr.ac.una.dbmonitor.repository.RendimientoRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
public class RendimientoService {
    private static final int TOP_MINIMO = 5;
    private static final int TOP_MAXIMO = 50;
    private static final int TOP_POR_DEFECTO = 10;
    private static final String ESTADO_ACTIVA = "ACTIVE";

    private final RendimientoRepository repository;

    public RendimientoService(RendimientoRepository repository) {
        this.repository = repository;
    }

    public RendimientoDto obtenerRendimiento(OrdenConsultas orden, Integer top) {
        var ordenAplicado = orden == null ? OrdenConsultas.TIEMPO_TOTAL : orden;
        var limite = limitar(top);
        var datos = repository.consultar(ordenAplicado, limite);
        var avisos = new ArrayList<>(datos.avisos());
        return new RendimientoDto(consultas(datos.consultas(), avisos), sesiones(datos.sesiones()),
                datos.procesos(), ordenAplicado, limite, avisos);
    }

    private int limitar(Integer top) {
        return top == null ? TOP_POR_DEFECTO : Math.clamp(top, TOP_MINIMO, TOP_MAXIMO);
    }

    private ConsultasTopDto consultas(ConsultasTopDto datos, List<String> avisos) {
        if (datos == null) {
            return null;
        }
        var consultas = datos.consultas() == null ? null
                : datos.consultas().stream().map(this::conPromedio).toList();
        return new ConsultasTopDto(conPromedio(datos.resumen(), avisos), consultas);
    }

    private ConsultasTopDto.Consulta conPromedio(ConsultasTopDto.Consulta consulta) {
        return new ConsultasTopDto.Consulta(consulta.sqlId(), consulta.texto(), consulta.ejecuciones(),
                consulta.tiempoTotalMicros(), promedio(consulta.tiempoTotalMicros(), consulta.ejecuciones()),
                consulta.cpuMicros(), consulta.lecturasMemoria(), consulta.lecturasDisco(),
                consulta.filasProcesadas(), consulta.ultimaActividad());
    }

    private ConsultasTopDto.Resumen conPromedio(ConsultasTopDto.Resumen resumen, List<String> avisos) {
        if (resumen == null) {
            return null;
        }
        var promedio = promedio(resumen.tiempoTotalMicros(), resumen.ejecuciones());
        if (promedio == null) {
            avisos.add("Tiempo promedio no disponible: Oracle no reportó ejecuciones ni tiempo acumulado.");
        }
        return new ConsultasTopDto.Resumen(resumen.ejecuciones(), resumen.tiempoTotalMicros(), promedio,
                resumen.cpuMicros(), resumen.lecturasMemoria(), resumen.lecturasDisco());
    }

    private BigDecimal promedio(BigDecimal total, BigDecimal ejecuciones) {
        if (total == null || ejecuciones == null || ejecuciones.signum() <= 0 || total.signum() < 0) {
            return null;
        }
        return total.divide(ejecuciones, 0, RoundingMode.HALF_UP);
    }

    private SesionesDto sesiones(SesionesDto datos) {
        if (datos == null) {
            return null;
        }
        Integer activas = null;
        Integer inactivas = null;
        if (datos.sesiones() != null) {
            activas = (int) datos.sesiones().stream().filter(s -> ESTADO_ACTIVA.equals(s.estado())).count();
            inactivas = datos.sesiones().size() - activas;
        }
        return new SesionesDto(datos.sesiones(), datos.bloqueos(), activas, inactivas);
    }
}
