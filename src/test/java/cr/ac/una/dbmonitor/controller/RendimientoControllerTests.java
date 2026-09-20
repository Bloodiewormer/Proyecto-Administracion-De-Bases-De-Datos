package cr.ac.una.dbmonitor.controller;

import cr.ac.una.dbmonitor.dto.ConsultasTopDto;
import cr.ac.una.dbmonitor.dto.OrdenConsultas;
import cr.ac.una.dbmonitor.dto.ProcesosDto;
import cr.ac.una.dbmonitor.dto.RendimientoDto;
import cr.ac.una.dbmonitor.dto.SesionesDto;
import cr.ac.una.dbmonitor.service.RendimientoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class RendimientoControllerTests {
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private RendimientoService service;

    private RendimientoDto vacio(List<String> avisos) {
        return new RendimientoDto(null, null, null, OrdenConsultas.TIEMPO_TOTAL, 10, avisos);
    }

    @Test
    void renderizaConsultasSesionesYProcesosConUnidades() throws Exception {
        var momento = LocalDateTime.of(2026, 3, 4, 5, 6, 7);
        var consulta = new ConsultasTopDto.Consulta("sql-prueba", "SELECT * FROM EMPLEADOS",
                new BigDecimal("40"), new BigDecimal("8000000"), new BigDecimal("200000"),
                new BigDecimal("2000000"), new BigDecimal("500"), new BigDecimal("30"),
                new BigDecimal("40"), momento);
        var resumen = new ConsultasTopDto.Resumen(new BigDecimal("40"), new BigDecimal("8000000"),
                new BigDecimal("200000"), new BigDecimal("2000000"), new BigDecimal("500"), new BigDecimal("30"));
        var sesion = new SesionesDto.Sesion(101, 9, "MONITOR", "ACTIVE", "so-prueba", "equipo-prueba",
                "sqlplus.exe", momento, new BigDecimal("90"), "sql-prueba",
                "SQL*Net message from client", "WAITING", null);
        var proceso = new ProcesosDto.ProcesoMemoria(28, "4321", "MMON", "oracle.exe", "BACKGROUND",
                new BigDecimal("2097152"), new BigDecimal("1048576"), new BigDecimal("3145728"),
                101, 9, "MONITOR", "ACTIVE", "equipo-prueba");
        var cpu = new ProcesosDto.SesionCpu(101, 9, "MONITOR", "sqlplus.exe", "ACTIVE", "USER",
                new BigDecimal("350"));
        when(service.obtenerRendimiento(any(), any())).thenReturn(new RendimientoDto(
                new ConsultasTopDto(resumen, List.of(consulta)),
                new SesionesDto(List.of(sesion), List.of(), 1, 0),
                new ProcesosDto(List.of(proceso), List.of(cpu)), OrdenConsultas.CPU, 25, List.of()));
        mvc.perform(get("/rendimiento")).andExpect(status().isOk()).andExpect(view().name("rendimiento"))
                .andExpect(content().string(containsString("sql-prueba")))
                .andExpect(content().string(containsString("SELECT * FROM EMPLEADOS")))
                .andExpect(content().string(containsString("8.00 s")))
                .andExpect(content().string(containsString("200.00 ms")))
                .andExpect(content().string(containsString("2.00 MiB")))
                .andExpect(content().string(containsString("3.50 s")))
                .andExpect(content().string(containsString("0 d 0 h 1 min 30 s")))
                .andExpect(content().string(containsString("04/03/2026 05:06:07")))
                .andExpect(content().string(containsString("Tiempo de CPU")))
                .andExpect(content().string(containsString("No se detectaron sesiones bloqueadas")))
                .andExpect(content().string(containsString("1 activas / 0 inactivas")))
                .andExpect(content().string(containsString("Interno de Oracle")))
                .andExpect(content().string(containsString("MMON")))
                .andExpect(content().string(containsString("href=\"/estado\"")))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void informaLosBloqueosDetectados() throws Exception {
        var bloqueo = new SesionesDto.Bloqueo(55, 3, "VENTAS", "enq: TX - row lock contention",
                new BigDecimal("120"), "sql-bloqueada", 77, "NOMINA", "app.exe", "INACTIVE");
        when(service.obtenerRendimiento(any(), any())).thenReturn(new RendimientoDto(null,
                new SesionesDto(List.of(), List.of(bloqueo), 0, 0), null,
                OrdenConsultas.TIEMPO_TOTAL, 10, List.of()));
        mvc.perform(get("/rendimiento")).andExpect(status().isOk())
                .andExpect(content().string(containsString("enq: TX - row lock contention")))
                .andExpect(content().string(containsString("Se detectaron 1 sesiones en espera")))
                .andExpect(content().string(containsString("0 d 0 h 2 min 0 s")))
                .andExpect(content().string(containsString("NOMINA")))
                .andExpect(content().string(not(containsString("No se detectaron sesiones bloqueadas"))));
    }

    @Test
    void parametrosInvalidosNoRompenLaPagina() throws Exception {
        when(service.obtenerRendimiento(any(), any())).thenReturn(vacio(List.of()));
        mvc.perform(get("/rendimiento").param("orden", "'; DROP TABLE USUARIOS--").param("top", "abc"))
                .andExpect(status().isOk());
        verify(service).obtenerRendimiento(OrdenConsultas.TIEMPO_TOTAL, null);
    }

    @Test
    void parametrosValidosLleganAlServicio() throws Exception {
        when(service.obtenerRendimiento(any(), any())).thenReturn(vacio(List.of()));
        mvc.perform(get("/rendimiento").param("orden", "cpu").param("top", "25"))
                .andExpect(status().isOk());
        verify(service).obtenerRendimiento(OrdenConsultas.CPU, 25);
    }

    @Test
    void sinConexionMuestraAvisoYEnlace() throws Exception {
        when(service.obtenerRendimiento(any(), any())).thenReturn(
                vacio(List.of("No hay conexión configurada. Conecte una instancia Oracle.")));
        mvc.perform(get("/rendimiento")).andExpect(status().isOk())
                .andExpect(content().string(containsString("No hay conexión configurada")))
                .andExpect(content().string(containsString("href=\"/conexion\"")))
                .andExpect(content().string(containsString("No disponible")))
                .andExpect(content().string(not(containsString("Exception"))));
    }

    @Test
    void noExponeNombresDeVistasSqlEnLosAvisos() throws Exception {
        when(service.obtenerRendimiento(any(), any())).thenReturn(vacio(List.of(
                "V$SQLSTATS: permisos insuficientes.", "V$SESSION: vista no accesible o inexistente.",
                "V$PROCESS: permisos insuficientes.", "V$SESSTAT: permisos insuficientes.")));
        mvc.perform(get("/rendimiento")).andExpect(status().isOk())
                .andExpect(content().string(containsString("permisos insuficientes")))
                .andExpect(content().string(not(containsString("V$SQLSTATS"))))
                .andExpect(content().string(not(containsString("V$SESSION"))))
                .andExpect(content().string(not(containsString("V$PROCESS"))))
                .andExpect(content().string(not(containsString("V$SESSTAT"))));
    }

    @Test
    void escapaElTextoSqlDevueltoPorOracle() throws Exception {
        var consulta = new ConsultasTopDto.Consulta("sql-prueba", "<script>alert(1)</script>",
                null, null, null, null, null, null, null, null);
        when(service.obtenerRendimiento(any(), any())).thenReturn(new RendimientoDto(
                new ConsultasTopDto(null, List.of(consulta)), null, null,
                OrdenConsultas.TIEMPO_TOTAL, 10, List.of()));
        mvc.perform(get("/rendimiento")).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("<script>alert(1)</script>"))))
                .andExpect(content().string(containsString("&lt;script&gt;")));
    }
}
