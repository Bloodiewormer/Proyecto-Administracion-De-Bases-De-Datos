package cr.ac.una.dbmonitor.controller;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FormatosTests {

    private final Formatos formatos = new Formatos();

    @Test
    void formateaMemoriaPositivaYNegativa() {
        assertEquals("1.00 GiB", formatos.memoria(new BigDecimal("1073741824")));
        assertEquals("-1.00 GiB", formatos.memoria(new BigDecimal("-1073741824")));
        assertEquals("50.00 MiB", formatos.memoria(new BigDecimal("52428800")));
        assertEquals("-50.00 MiB", formatos.memoria(new BigDecimal("-52428800")));
        assertEquals("10.00 KiB", formatos.memoria(new BigDecimal("10240")));
        assertEquals("-10.00 KiB", formatos.memoria(new BigDecimal("-10240")));
        assertEquals("500 bytes", formatos.memoria(new BigDecimal("500")));
        assertEquals("-500 bytes", formatos.memoria(new BigDecimal("-500")));
        assertEquals("No disponible", formatos.memoria(null));
    }

    @Test
    void formateaPorcentaje() {
        assertEquals("45.68 %", formatos.porcentaje(new BigDecimal("45.678")));
        assertEquals("0.00 %", formatos.porcentaje(BigDecimal.ZERO));
        assertEquals("No disponible", formatos.porcentaje(null));
    }

    @Test
    void formateaEnterosYTexto() {
        assertEquals("42", formatos.entero(42));
        assertEquals("No disponible", formatos.entero(null));
        assertEquals("valor", formatos.texto("valor"));
        assertEquals("No disponible", formatos.texto("   "));
        assertEquals("No disponible", formatos.texto(null));
    }
}
