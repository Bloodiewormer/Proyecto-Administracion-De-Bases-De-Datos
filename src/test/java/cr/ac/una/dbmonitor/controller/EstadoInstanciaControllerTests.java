package cr.ac.una.dbmonitor.controller;

import cr.ac.una.dbmonitor.dto.BaseDatosDto;
import cr.ac.una.dbmonitor.dto.EstadoInstanciaDto;
import cr.ac.una.dbmonitor.dto.MemoriaInstanciaDto;
import cr.ac.una.dbmonitor.service.EstadoInstanciaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class EstadoInstanciaControllerTests {
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private EstadoInstanciaService service;

    @Test
    void renderizaDtoCompletoSinCredenciales() throws Exception {
        var fecha = LocalDateTime.of(2026, 1, 2, 3, 4, 5);
        var bytes = new BigDecimal("1073741824");
        var uso = new MemoriaInstanciaDto.Uso(new BigDecimal("268435456"), new BigDecimal("805306368"),
                new BigDecimal("209715200"), new BigDecimal("104857600"));
        var datos = new EstadoInstanciaDto(
                new EstadoInstanciaDto.Instancia("host-prueba", "instancia-prueba", "version-prueba", "XE",
                        "SINGLE", "OPEN", fecha, fecha.plusDays(1)),
                Duration.ofDays(1).plusMinutes(2),
                new MemoriaInstanciaDto(List.of(new MemoriaInstanciaDto.Componente("Fixed Size", bytes, 0)),
                        bytes, uso),
                new BaseDatosDto(new BaseDatosDto.Base("base-prueba", "READ WRITE", "YES"),
                        List.of(new BaseDatosDto.Contenedor(1, "raiz-prueba", "READ WRITE", false),
                                new BaseDatosDto.Contenedor(3, "pdb-prueba", "READ ONLY", true))), List.of());
        when(service.obtenerEstado()).thenReturn(datos);
        mvc.perform(get("/estado")).andExpect(status().isOk()).andExpect(view().name("estado"))
                .andExpect(model().attribute("estado", sameInstance(datos)))
                .andExpect(content().string(containsString("host-prueba")))
                .andExpect(content().string(containsString("instancia-prueba")))
                .andExpect(content().string(containsString("version-prueba")))
                .andExpect(content().string(containsString("Oracle Database - XE")))
                .andExpect(content().string(containsString("SINGLE")))
                .andExpect(content().string(containsString("02/01/2026 03:04:05")))
                .andExpect(content().string(containsString("1 d 0 h 2 min 0 s")))
                .andExpect(content().string(containsString("1.00 GiB")))
                .andExpect(content().string(containsString("1073741824")))
                .andExpect(content().string(containsString("768.00 MiB")))
                .andExpect(content().string(containsString("256.00 MiB")))
                .andExpect(content().string(containsString("200.00 MiB")))
                .andExpect(content().string(containsString("100.00 MiB")))
                .andExpect(content().string(containsString("raiz-prueba")))
                .andExpect(content().string(containsString("pdb-prueba")))
                .andExpect(content().string(containsString("READ ONLY")))
                .andExpect(content().string(not(containsString("password"))))
                .andExpect(content().string(not(containsString("username"))))
                .andExpect(model().attributeDoesNotExist("conexion", "password", "username"))
                .andExpect(header().string("Cache-Control", "no-store"));
        verify(service).obtenerEstado();
    }

    @Test
    void sinConexionMuestraAvisoYEnlace() throws Exception {
        when(service.obtenerEstado()).thenReturn(new EstadoInstanciaDto(null, null, null, null,
                List.of("No hay conexión configurada. Conecte una instancia Oracle.")));
        mvc.perform(get("/estado")).andExpect(status().isOk()).andExpect(view().name("estado"))
                .andExpect(content().string(containsString("No hay conexión configurada")))
                .andExpect(content().string(containsString("href=\"/conexion\"")))
                .andExpect(content().string(containsString("No disponible")))
                .andExpect(content().string(not(containsString("Oracle Database - "))))
                .andExpect(content().string(not(containsString("Exception"))));
    }

    @Test
    void datosParcialesNoRompenVistaNiExponenNombresDeVistasSql() throws Exception {
        when(service.obtenerEstado()).thenReturn(new EstadoInstanciaDto(
                new EstadoInstanciaDto.Instancia(null, null, null, null, null, null, null, null), null,
                new MemoriaInstanciaDto(List.of(new MemoriaInstanciaDto.Componente(null, null, null)), null, null),
                new BaseDatosDto(null, null),
                List.of("V$INSTANCE: permisos insuficientes.", "V$DATABASE: vista no accesible o inexistente.",
                        "V$CONTAINERS: permisos insuficientes.",
                        "V$SGASTAT/V$PGASTAT: permisos insuficientes.",
                        "Total SGA no disponible: no hay componentes de ámbito global (CON_ID = 0).")));
        mvc.perform(get("/estado")).andExpect(status().isOk())
                .andExpect(content().string(containsString("permisos insuficientes")))
                .andExpect(content().string(containsString("No disponible")))
                .andExpect(content().string(not(containsString("V$INSTANCE"))))
                .andExpect(content().string(not(containsString("V$DATABASE"))))
                .andExpect(content().string(not(containsString("V$CONTAINERS"))))
                .andExpect(content().string(not(containsString("PGASTAT"))))
                .andExpect(content().string(not(containsString("CON_ID"))));
    }

    @Test
    void escapaContenidoDeOracle() throws Exception {
        when(service.obtenerEstado()).thenReturn(new EstadoInstanciaDto(
                new EstadoInstanciaDto.Instancia("<script>alert(1)</script>", null, null, null, null, null,
                        null, null),
                null, null, null, List.of()));
        mvc.perform(get("/estado")).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("<script>alert(1)</script>"))))
                .andExpect(content().string(containsString("&lt;script&gt;")));
    }
}
