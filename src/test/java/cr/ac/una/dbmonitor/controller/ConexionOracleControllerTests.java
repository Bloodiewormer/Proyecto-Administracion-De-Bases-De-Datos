package cr.ac.una.dbmonitor.controller;

import cr.ac.una.dbmonitor.service.ConexionOracleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ConexionOracleControllerTests {
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ConexionOracleService service;

    @Test
    void formularioSinCredencialesPredeterminadas() throws Exception {
        mvc.perform(get("/conexion")).andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"localhost\"")))
                .andExpect(content().string(containsString("value=\"1521\"")))
                .andExpect(content().string(not(matchesPattern("(?s).*<input[^>]*id=\"password\"[^>]*value=.*"))));
        verifyNoInteractions(service);
    }

    @Test
    void validacionImpideConexionYNoDevuelvePassword() throws Exception {
        mvc.perform(post("/conexion").param("host", "localhost?x=1")
                        .param("port", "invalido").param("password", "secreto-de-prueba"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("conexion", "host", "port", "serviceName", "username"))
                .andExpect(content().string(not(containsString("secreto-de-prueba"))));
        verifyNoInteractions(service);
    }

    @Test
    void exitoNoExponePassword() throws Exception {
        mvc.perform(post("/conexion").param("host", "localhost").param("port", "1521")
                        .param("serviceName", "SERVICIO_PRUEBA").param("username", "usuario_prueba")
                        .param("password", "secreto-de-prueba"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Conexión establecida correctamente.")))
                .andExpect(content().string(not(containsString("secreto-de-prueba"))))
                .andExpect(content().string(not(matchesPattern("(?s).*<input[^>]*id=\"password\"[^>]*value=.*"))))
                .andExpect(header().string("Cache-Control", "no-store"));
        verify(service).conectar(any());
    }

    @Test
    void errorNoExponePassword() throws Exception {
        doThrow(new IllegalStateException("Credenciales inválidas.")).when(service).conectar(any());
        mvc.perform(post("/conexion").param("host", "localhost").param("port", "1521")
                        .param("serviceName", "SERVICIO_PRUEBA").param("username", "usuario_prueba")
                        .param("password", "secreto-de-prueba"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Credenciales inválidas.")))
                .andExpect(content().string(not(containsString("secreto-de-prueba"))));
    }
}
