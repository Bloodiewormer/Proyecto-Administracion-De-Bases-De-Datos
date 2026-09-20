package cr.ac.una.dbmonitor.service;

import cr.ac.una.dbmonitor.dto.EstadoInstanciaDto;
import cr.ac.una.dbmonitor.dto.MemoriaInstanciaDto;
import cr.ac.una.dbmonitor.repository.EstadoInstanciaRepository;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EstadoInstanciaServiceTests {
    private final EstadoInstanciaRepository repository = mock(EstadoInstanciaRepository.class);
    private final EstadoInstanciaService service = new EstadoInstanciaService(repository);

    private EstadoInstanciaDto calcular(EstadoInstanciaDto.Instancia instancia,
                                        List<MemoriaInstanciaDto.Componente> componentes) {
        return calcular(instancia, componentes, null);
    }

    private EstadoInstanciaDto calcular(EstadoInstanciaDto.Instancia instancia,
                                        List<MemoriaInstanciaDto.Componente> componentes,
                                        MemoriaInstanciaDto.Uso uso) {
        when(repository.consultar()).thenReturn(new EstadoInstanciaDto(instancia, null,
                new MemoriaInstanciaDto(componentes, null, uso), null, List.of("aviso previo")));
        return service.obtenerEstado();
    }

    @Test
    void calculaUptimeConFechasOracle() {
        var inicio = LocalDateTime.of(2026, 1, 1, 10, 0);
        var instancia = new EstadoInstanciaDto.Instancia("host", "instancia", "version", "XE", "SINGLE", "OPEN",
                inicio, inicio.plusDays(2).plusSeconds(45));
        var datos = calcular(instancia, List.of());
        assertEquals(Duration.ofDays(2).plusSeconds(45), datos.uptime());
        assertTrue(datos.avisos().contains("aviso previo"));
    }

    @Test
    void fechasAusentesONegativasNoInventanUptime() {
        var ahora = LocalDateTime.of(2026, 1, 1, 10, 0);
        assertNull(calcular(new EstadoInstanciaDto.Instancia(null, null, null, null, null, null, null, ahora),
                List.of()).uptime());
        assertNull(calcular(new EstadoInstanciaDto.Instancia(null, null, null, null, null, null, ahora,
                ahora.minusSeconds(1)), List.of()).uptime());
    }

    @Test
    void sumaGlobalSinMezclarContenedoresNiDesbordarEnteros() {
        var datos = calcular(null, List.of(
                new MemoriaInstanciaDto.Componente("Fixed Size", new BigDecimal("4294967296"), 0),
                new MemoriaInstanciaDto.Componente("Variable Size", new BigDecimal("1024"), 0),
                new MemoriaInstanciaDto.Componente("Fixed Size", new BigDecimal("999"), 3)));
        assertEquals(new BigDecimal("4294968320"), datos.memoria().totalSgaBytes());
        assertEquals(3, datos.memoria().componentes().size());
    }

    @Test
    void sinComponentesGlobalesTotalNoDisponible() {
        assertNull(calcular(null, List.of()).memoria().totalSgaBytes());
        assertNull(calcular(null, List.of(
                new MemoriaInstanciaDto.Componente("componente", BigDecimal.TEN, 3))).memoria().totalSgaBytes());
    }

    @Test
    void componentesIncompletosNegativosODuplicadosNoGeneranTotal() {
        var valido = new MemoriaInstanciaDto.Componente("componente", BigDecimal.TEN, 0);
        assertNull(calcular(null, List.of(valido,
                new MemoriaInstanciaDto.Componente("otro", null, 0))).memoria().totalSgaBytes());
        assertNull(calcular(null, List.of(
                new MemoriaInstanciaDto.Componente("otro", BigDecimal.valueOf(-1), 0))).memoria().totalSgaBytes());
        assertNull(calcular(null, List.of(valido, valido)).memoria().totalSgaBytes());
    }

    @Test
    void restaLaMemoriaLibreParaObtenerLaSgaUtilizada() {
        var componentes = List.of(new MemoriaInstanciaDto.Componente("Fixed Size", new BigDecimal("1000"), 0));
        var datos = calcular(null, componentes, new MemoriaInstanciaDto.Uso(new BigDecimal("400"), null,
                new BigDecimal("50"), new BigDecimal("20")));
        assertEquals(new BigDecimal("600"), datos.memoria().uso().sgaUtilizadaBytes());
        assertEquals(new BigDecimal("50"), datos.memoria().uso().pgaAsignadaBytes());
        assertEquals(new BigDecimal("20"), datos.memoria().uso().pgaUtilizadaBytes());
    }

    @Test
    void memoriaLibreAusenteOInconsistenteNoInventaSgaUtilizada() {
        var componentes = List.of(new MemoriaInstanciaDto.Componente("Fixed Size", new BigDecimal("1000"), 0));
        var sinLibre = calcular(null, componentes,
                new MemoriaInstanciaDto.Uso(null, null, null, null));
        assertNull(sinLibre.memoria().uso().sgaUtilizadaBytes());
        assertTrue(sinLibre.avisos().stream().anyMatch(a -> a.startsWith("Memoria SGA utilizada no disponible")));
        var libreMayor = calcular(null, componentes,
                new MemoriaInstanciaDto.Uso(new BigDecimal("5000"), null, null, null));
        assertNull(libreMayor.memoria().uso().sgaUtilizadaBytes());
        var sinTotal = calcular(null, List.of(
                new MemoriaInstanciaDto.Componente("Fixed Size", new BigDecimal("1000"), 3)),
                new MemoriaInstanciaDto.Uso(new BigDecimal("400"), null, null, null));
        assertNull(sinTotal.memoria().uso().sgaUtilizadaBytes());
    }

    @Test
    void sinConexionConservaAusenciaYAviso() {
        when(repository.consultar()).thenReturn(new EstadoInstanciaDto(null, null, null, null,
                List.of("No hay conexión configurada.")));
        var datos = service.obtenerEstado();
        assertNull(datos.memoria());
        assertNull(datos.uptime());
        assertEquals(List.of("No hay conexión configurada."), datos.avisos());
    }

    @Test
    void ceroReportadoEsDistintoDeAusencia() {
        assertEquals(BigDecimal.ZERO, calcular(null, List.of(
                new MemoriaInstanciaDto.Componente("componente", BigDecimal.ZERO, 0))).memoria().totalSgaBytes());
    }
}
