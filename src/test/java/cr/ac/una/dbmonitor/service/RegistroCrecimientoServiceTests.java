package cr.ac.una.dbmonitor.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class RegistroCrecimientoServiceTests {

    @Test
    void calculaVariacionEntreMedicionesConsecutivas() {
        var service = new RegistroCrecimientoService();

        service.registrarMedicion(new BigDecimal("1000"), new BigDecimal("500"), new BigDecimal("500"));
        service.registrarMedicion(new BigDecimal("1200"), new BigDecimal("600"), new BigDecimal("600"));

        var mediciones = service.obtenerMediciones();

        assertFalse(mediciones.isEmpty());
        assertTrue(mediciones.size() >= 2);

        // La primera en la lista es la más reciente (600 usado)
        var masReciente = mediciones.getFirst();
        assertEquals(new BigDecimal("1200"), masReciente.tamanoTotalBytes());
        assertEquals(new BigDecimal("600"), masReciente.usadoBytes());
        assertEquals(new BigDecimal("100"), masReciente.variacionBytes());
        assertEquals(new BigDecimal("20.00"), masReciente.variacionPorcentual());
    }

    @Test
    void valoresNulosNoRompenElServicio() {
        var service = new RegistroCrecimientoService();
        assertDoesNotThrow(() -> service.registrarMedicion(null, null, null));
        assertNotNull(service.obtenerMediciones());
    }
}
