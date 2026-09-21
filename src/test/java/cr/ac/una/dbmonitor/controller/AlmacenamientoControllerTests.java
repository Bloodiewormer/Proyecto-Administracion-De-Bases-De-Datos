package cr.ac.una.dbmonitor.controller;

import cr.ac.una.dbmonitor.dto.AlmacenamientoDto;
import cr.ac.una.dbmonitor.dto.ArchivoDatosDto;
import cr.ac.una.dbmonitor.dto.CrecimientoDto;
import cr.ac.una.dbmonitor.dto.FiltroTipoObjeto;
import cr.ac.una.dbmonitor.dto.ObjetoAlmacenamientoDto;
import cr.ac.una.dbmonitor.dto.ResumenAlmacenamientoDto;
import cr.ac.una.dbmonitor.dto.TablespaceDto;
import cr.ac.una.dbmonitor.service.AlmacenamientoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AlmacenamientoControllerTests {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AlmacenamientoService service;

    private AlmacenamientoDto vacio(List<String> avisos) {
        return new AlmacenamientoDto(null, List.of(), List.of(), List.of(),
                new CrecimientoDto(List.of(), List.of()), FiltroTipoObjeto.TODOS, 10, avisos);
    }

    @Test
    void renderizaAlmacenamientoCorrectamente() throws Exception {
        var resumen = new ResumenAlmacenamientoDto(
                new BigDecimal("104857600"), // 100 MiB
                new BigDecimal("52428800"),  // 50 MiB
                new BigDecimal("52428800"),  // 50 MiB
                new BigDecimal("50.00"),
                new BigDecimal("83886080"),
                new BigDecimal("10485760"),
                new BigDecimal("10485760"),
                2, 2
        );

        var ts = new TablespaceDto("TBS_PRUEBA", "PERMANENT", "ONLINE", "LOCAL", "AUTO",
                new BigDecimal("83886080"), new BigDecimal("41943040"), new BigDecimal("41943040"),
                new BigDecimal("104857600"), new BigDecimal("50.00"));

        var arch = new ArchivoDatosDto(1, "/u01/app/oracle/oradata/XE/tbs_prueba01.dbf",
                "TBS_PRUEBA", new BigDecimal("83886080"), new BigDecimal("104857600"),
                "YES", new BigDecimal("1280"), "AVAILABLE", "DATA");

        var obj = new ObjetoAlmacenamientoDto("HR", "EMPLEADOS", null, "TABLE",
                "TBS_PRUEBA", new BigDecimal("1048576"), new BigDecimal("128"), new BigDecimal("2"));

        var med = new CrecimientoDto.Medicion(LocalDateTime.of(2026, 3, 15, 10, 0, 0),
                new BigDecimal("104857600"), new BigDecimal("52428800"), new BigDecimal("52428800"),
                BigDecimal.ZERO, BigDecimal.ZERO);

        var crecimiento = new CrecimientoDto(List.of(med), List.of());

        var dto = new AlmacenamientoDto(resumen, List.of(ts), List.of(arch), List.of(obj),
                crecimiento, FiltroTipoObjeto.TODOS, 10, List.of());

        when(service.obtenerAlmacenamiento(any(), any())).thenReturn(dto);

        mvc.perform(get("/almacenamiento"))
                .andExpect(status().isOk())
                .andExpect(view().name("almacenamiento"))
                .andExpect(content().string(containsString("TBS_PRUEBA")))
                .andExpect(content().string(containsString("tbs_prueba01.dbf")))
                .andExpect(content().string(containsString("EMPLEADOS")))
                .andExpect(content().string(containsString("100.00 MiB")))
                .andExpect(content().string(containsString("50.00 %")));
    }

    @Test
    void muestraAvisosDeAlmacenamiento() throws Exception {
        when(service.obtenerAlmacenamiento(any(), any())).thenReturn(vacio(List.of(
                "No hay conexión configurada. Conecte una instancia Oracle.",
                "DBA_TABLESPACES: permisos insuficientes.",
                "DBA_HIST_TBSPC_SPACE_USAGE: vista no accesible o inexistente."
        )));

        mvc.perform(get("/almacenamiento"))
                .andExpect(status().isOk())
                .andExpect(view().name("almacenamiento"))
                .andExpect(content().string(containsString("No hay conexión configurada")))
                .andExpect(content().string(containsString("Tablespaces: permisos insuficientes")))
                .andExpect(content().string(containsString("Histórico AWR de Oracle: vista no accesible o inexistente")));
    }

    @Test
    void postSnapshotRegistraMedicionYRedirige() throws Exception {
        mvc.perform(post("/almacenamiento/snapshot"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/almacenamiento?snapshotExitoso=true"));

        verify(service).registrarMedicionActual();
    }
}
