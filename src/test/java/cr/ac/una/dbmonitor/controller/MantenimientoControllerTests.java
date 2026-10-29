package cr.ac.una.dbmonitor.controller;

import cr.ac.una.dbmonitor.dto.AuditoriaDto;
import cr.ac.una.dbmonitor.dto.Listado;
import cr.ac.una.dbmonitor.dto.MantenimientoDto;
import cr.ac.una.dbmonitor.dto.ResultadoMantenimiento;
import cr.ac.una.dbmonitor.dto.ResultadoMantenimiento.Nivel;
import cr.ac.una.dbmonitor.dto.VistaMantenimiento;
import cr.ac.una.dbmonitor.service.MantenimientoService;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class MantenimientoControllerTests {
    private static final AuditoriaDto.Contexto CONTEXTO = new AuditoriaDto.Contexto("FREEPDB1", true, "SYSTEM");
    private static final MantenimientoDto.Resumen RESUMEN = new MantenimientoDto.Resumen(1, 2, 3, 5);
    private static final LocalDateTime MOMENTO = LocalDateTime.of(2026, 10, 28, 9, 15, 0);

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private MantenimientoService service;

    private void responder(MantenimientoDto datos) {
        when(service.obtenerMantenimiento(any(), anyBoolean(), any())).thenReturn(datos);
    }

    @Test
    void muestraTablasPendientesConSuEstadoYAcciones() throws Exception {
        var sinEstadisticas = new MantenimientoDto.TablaEstadisticas("VENTAS", "CLIENTES", null, null, false, null);
        var desactualizada = new MantenimientoDto.TablaEstadisticas("VENTAS", "FACTURAS", new BigDecimal("1500"),
                MOMENTO, true, "ALL");
        responder(new MantenimientoDto(VistaMantenimiento.ESTADISTICAS, false, 200, CONTEXTO, RESUMEN,
                new Listado<>(List.of(sinEstadisticas, desactualizada), 2), List.of("VENTAS"), null, null, null,
                List.of()));
        mvc.perform(get("/mantenimiento")).andExpect(status().isOk()).andExpect(view().name("mantenimiento"))
                .andExpect(content().string(containsString("Sin estadísticas")))
                .andExpect(content().string(containsString("Desactualizadas")))
                .andExpect(content().string(containsString("Bloqueadas")))
                .andExpect(content().string(containsString("28/10/2026 09:15:00")))
                .andExpect(content().string(containsString("action=\"/mantenimiento/estadisticas/tabla\"")))
                .andExpect(content().string(containsString("action=\"/mantenimiento/estadisticas/esquema\"")))
                .andExpect(content().string(containsString("aria-current=\"page\">Mantenimiento")))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void muestraObjetosInvalidosYSiAdmitenRecompilacion() throws Exception {
        var procedimiento = new MantenimientoDto.ObjetoInvalido("VENTAS", "P_FACTURAR", "PROCEDURE", MOMENTO, 2, true);
        var java = new MantenimientoDto.ObjetoInvalido("VENTAS", "CLASE", "JAVA CLASS", MOMENTO, 0, false);
        var error = new AuditoriaDto.ErrorCompilacion("VENTAS", "P_FACTURAR", "PROCEDURE", 3, 12,
                "PL/SQL: ORA-00904: invalid identifier", "ERROR");
        responder(new MantenimientoDto(VistaMantenimiento.RECOMPILACION, false, 200, CONTEXTO, RESUMEN, null, null,
                new Listado<>(List.of(procedimiento, java), 2),
                List.of(new MantenimientoDto.EsquemaConInvalidos("VENTAS", 2)),
                new Listado<>(List.of(error), 1), List.of()));
        mvc.perform(get("/mantenimiento").param("vista", "RECOMPILACION")).andExpect(status().isOk())
                .andExpect(content().string(containsString("P_FACTURAR")))
                .andExpect(content().string(containsString("action=\"/mantenimiento/recompilar/objeto\"")))
                .andExpect(content().string(containsString("Recompile el esquema completo")))
                .andExpect(content().string(containsString("VENTAS (2 inválidos)")))
                .andExpect(content().string(containsString("ORA-00904")));
    }

    @Test
    void laOperacionRedirigeConElResultado() throws Exception {
        var resultado = new ResultadoMantenimiento(Nivel.ERROR, "Recompilar objeto", "VENTAS.P_FACTURAR (PROCEDURE)",
                "El objeto sigue en estado INVALID.", 42, MOMENTO);
        when(service.recompilarObjeto("VENTAS", "P_FACTURAR", "PROCEDURE")).thenReturn(resultado);
        mvc.perform(post("/mantenimiento/recompilar/objeto").param("esquema", "VENTAS").param("nombre", "P_FACTURAR")
                        .param("tipo", "PROCEDURE"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/mantenimiento?vista=RECOMPILACION"))
                .andExpect(flash().attribute("resultado", resultado));
    }

    @Test
    void conservaElFiltroDeTablasAlRedirigir() throws Exception {
        when(service.recalcularEstadisticasTabla("VENTAS", "FACTURAS")).thenReturn(new ResultadoMantenimiento(
                Nivel.EXITO, "Recalcular estadísticas de tabla", "VENTAS.FACTURAS", "Listo.", 10, MOMENTO));
        mvc.perform(post("/mantenimiento/estadisticas/tabla").param("esquema", "VENTAS").param("tabla", "FACTURAS")
                        .param("todas", "true"))
                .andExpect(redirectedUrl("/mantenimiento?vista=ESTADISTICAS&todas=true"));
    }

    @Test
    void muestraLaConfirmacionDelResultado() throws Exception {
        responder(new MantenimientoDto(VistaMantenimiento.ESTADISTICAS, false, 200, CONTEXTO, RESUMEN,
                new Listado<>(List.of(), 0), List.of(), null, null, null, List.of()));
        var resultado = new ResultadoMantenimiento(Nivel.ADVERTENCIA, "Recalcular estadísticas de esquema", "VENTAS",
                "Se actualizaron las estadísticas de 3 de 4 tablas del esquema.", 1250, MOMENTO);
        mvc.perform(get("/mantenimiento").flashAttr("resultado", resultado)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Operación completada con advertencias")))
                .andExpect(content().string(containsString("3 de 4 tablas")))
                .andExpect(content().string(containsString("Duración: 1250 ms")))
                .andExpect(content().string(containsString("alert-warning")))
                .andExpect(content().string(containsString("Todas las tablas de la organización tienen estadísticas al día")));
    }

    @Test
    void distingueFaltaDePermisosDeAusenciaDeProblemas() throws Exception {
        responder(new MantenimientoDto(VistaMantenimiento.RECOMPILACION, false, 200, CONTEXTO, null, null, null,
                null, null, new Listado<>(List.of(), 0),
                List.of("DBA_OBJECTS: vista no accesible o inexistente.")));
        mvc.perform(get("/mantenimiento").param("vista", "RECOMPILACION")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Objetos inválidos (DBA_OBJECTS): vista no accesible")))
                .andExpect(content().string(containsString("ANALYZE ANY")))
                .andExpect(content().string(containsString("No disponible")))
                .andExpect(content().string(containsString("No hay errores de compilación registrados")));
    }
}
