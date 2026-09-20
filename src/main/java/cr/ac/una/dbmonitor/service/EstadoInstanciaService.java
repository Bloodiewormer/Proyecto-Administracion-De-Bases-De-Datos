package cr.ac.una.dbmonitor.service;

import cr.ac.una.dbmonitor.dto.EstadoInstanciaDto;
import cr.ac.una.dbmonitor.dto.MemoriaInstanciaDto;
import cr.ac.una.dbmonitor.repository.EstadoInstanciaRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

@Service
public class EstadoInstanciaService {
    private final EstadoInstanciaRepository repository;

    public EstadoInstanciaService(EstadoInstanciaRepository repository) {
        this.repository = repository;
    }

    public EstadoInstanciaDto obtenerEstado() {
        var datos = repository.consultar();
        var avisos = new ArrayList<>(datos.avisos());
        Duration uptime = null;
        var instancia = datos.instancia();
        if (instancia != null) {
            if (instancia.inicio() == null || instancia.fechaConsulta() == null) {
                avisos.add("Tiempo de actividad no disponible: faltan fechas de Oracle.");
            } else {
                var diferencia = Duration.between(instancia.inicio(), instancia.fechaConsulta());
                if (diferencia.isNegative()) {
                    avisos.add("Tiempo de actividad no disponible: las fechas de Oracle son inconsistentes.");
                } else {
                    uptime = diferencia;
                }
            }
        }
        var memoria = datos.memoria();
        if (memoria != null) {
            var totalSga = totalSga(memoria, avisos);
            memoria = new MemoriaInstanciaDto(memoria.componentes(), totalSga,
                    uso(totalSga, memoria.uso(), avisos));
        }
        return new EstadoInstanciaDto(instancia, uptime, memoria, datos.baseDatos(), avisos);
    }

    private BigDecimal totalSga(MemoriaInstanciaDto memoria, List<String> avisos) {
        var globales = memoria.componentes().stream().filter(c -> Integer.valueOf(0).equals(c.conId())).toList();
        if (globales.isEmpty()) {
            avisos.add("Total SGA no disponible: no hay componentes de ámbito global (CON_ID = 0).");
            return null;
        }
        var nombres = new HashSet<String>();
        var total = BigDecimal.ZERO;
        for (var componente : globales) {
            if (componente.nombre() == null || componente.bytes() == null || componente.bytes().signum() < 0
                    || !nombres.add(componente.nombre())) {
                avisos.add("Total SGA no disponible: componentes incompletos, duplicados o inválidos.");
                return null;
            }
            total = total.add(componente.bytes());
        }
        return total;
    }

    private MemoriaInstanciaDto.Uso uso(BigDecimal totalSga, MemoriaInstanciaDto.Uso uso, List<String> avisos) {
        if (uso == null) {
            return null;
        }
        BigDecimal utilizada = null;
        if (uso.sgaLibreBytes() == null) {
            avisos.add("Memoria SGA utilizada no disponible: Oracle no reportó la memoria libre.");
        } else if (totalSga == null) {
            avisos.add("Memoria SGA utilizada no disponible: falta el total de la SGA.");
        } else if (totalSga.compareTo(uso.sgaLibreBytes()) < 0) {
            avisos.add("Memoria SGA utilizada no disponible: los valores de Oracle son inconsistentes.");
        } else {
            utilizada = totalSga.subtract(uso.sgaLibreBytes());
        }
        return new MemoriaInstanciaDto.Uso(uso.sgaLibreBytes(), utilizada,
                uso.pgaAsignadaBytes(), uso.pgaUtilizadaBytes());
    }
}
