package cr.ac.una.dbmonitor.service;

import cr.ac.una.dbmonitor.dto.ConsultasTopDto;
import cr.ac.una.dbmonitor.dto.OrdenConsultas;
import cr.ac.una.dbmonitor.dto.ProcesosDto;
import cr.ac.una.dbmonitor.dto.RendimientoDto;
import cr.ac.una.dbmonitor.dto.SesionesDto;
import cr.ac.una.dbmonitor.repository.RendimientoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class RendimientoServiceTests {
    private final RendimientoRepository repository = mock(RendimientoRepository.class);
    private final RendimientoService service = new RendimientoService(repository);

    private ConsultasTopDto.Consulta consulta(String total, String ejecuciones) {
        return new ConsultasTopDto.Consulta("sql-1", "SELECT 1", ejecuciones == null ? null : new BigDecimal(ejecuciones),
                total == null ? null : new BigDecimal(total), null, null, null, null, null, null);
    }

    private SesionesDto.Sesion sesion(String estado) {
        return new SesionesDto.Sesion(1, 2, "usuario", estado, null, null, null, null, null, null, null, null, null);
    }

    private RendimientoDto respuesta(ConsultasTopDto consultas, SesionesDto sesiones) {
        var datos = new RendimientoDto(consultas, sesiones, new ProcesosDto(List.of(), List.of()),
                OrdenConsultas.TIEMPO_TOTAL, 10, List.of("aviso previo"));
        when(repository.consultar(any(), anyInt())).thenReturn(datos);
        return service.obtenerRendimiento(OrdenConsultas.TIEMPO_TOTAL, 10);
    }

    @Test
    void calculaElPromedioPorConsultaYElGlobal() {
        var resumen = new ConsultasTopDto.Resumen(new BigDecimal("4"), new BigDecimal("1000"), null,
                null, null, null);
        var datos = respuesta(new ConsultasTopDto(resumen, List.of(consulta("900", "3"))), null);
        assertEquals(new BigDecimal("300"), datos.consultas().consultas().getFirst().tiempoPromedioMicros());
        assertEquals(new BigDecimal("250"), datos.consultas().resumen().tiempoPromedioMicros());
        assertTrue(datos.avisos().contains("aviso previo"));
    }

    @Test
    void ejecucionesAusentesOEnCeroNoInventanPromedio() {
        assertNull(respuesta(new ConsultasTopDto(null, List.of(consulta("900", "0"))), null)
                .consultas().consultas().getFirst().tiempoPromedioMicros());
        assertNull(respuesta(new ConsultasTopDto(null, List.of(consulta("900", null))), null)
                .consultas().consultas().getFirst().tiempoPromedioMicros());
        assertNull(respuesta(new ConsultasTopDto(null, List.of(consulta(null, "5"))), null)
                .consultas().consultas().getFirst().tiempoPromedioMicros());
    }

    @Test
    void resumenSinEjecucionesAvisaEnLugarDeMostrarCero() {
        var resumen = new ConsultasTopDto.Resumen(BigDecimal.ZERO, new BigDecimal("1000"), null,
                null, null, null);
        var datos = respuesta(new ConsultasTopDto(resumen, List.of()), null);
        assertNull(datos.consultas().resumen().tiempoPromedioMicros());
        assertTrue(datos.avisos().stream().anyMatch(a -> a.startsWith("Tiempo promedio no disponible")));
    }

    @Test
    void cuentaSesionesActivasEInactivas() {
        var datos = respuesta(null, new SesionesDto(
                List.of(sesion("ACTIVE"), sesion("INACTIVE"), sesion("ACTIVE")), List.of(), null, null));
        assertEquals(2, datos.sesiones().activas());
        assertEquals(1, datos.sesiones().inactivas());
    }

    @Test
    void sesionesNoDisponiblesNoSeCuentanComoCero() {
        var datos = respuesta(null, new SesionesDto(null, null, null, null));
        assertNull(datos.sesiones().activas());
        assertNull(datos.sesiones().inactivas());
    }

    @ParameterizedTest
    @CsvSource({"1,5", "4,5", "5,5", "10,10", "50,50", "51,50", "9999,50"})
    void limitaElTopAlRangoPermitido(int solicitado, int esperado) {
        when(repository.consultar(any(), anyInt())).thenReturn(new RendimientoDto(null, null, null,
                OrdenConsultas.TIEMPO_TOTAL, esperado, List.of()));
        assertEquals(esperado, service.obtenerRendimiento(OrdenConsultas.TIEMPO_TOTAL, solicitado).top());
        verify(repository).consultar(OrdenConsultas.TIEMPO_TOTAL, esperado);
    }

    @Test
    void topYOrdenAusentesUsanLosValoresPorDefecto() {
        when(repository.consultar(any(), anyInt())).thenReturn(new RendimientoDto(null, null, null,
                OrdenConsultas.TIEMPO_TOTAL, 10, List.of()));
        var datos = service.obtenerRendimiento(null, null);
        assertEquals(OrdenConsultas.TIEMPO_TOTAL, datos.orden());
        assertEquals(10, datos.top());
        verify(repository).consultar(eq(OrdenConsultas.TIEMPO_TOTAL), eq(10));
    }
}
