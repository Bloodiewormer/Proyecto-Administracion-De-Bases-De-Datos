package cr.ac.una.dbmonitor.service;

import cr.ac.una.dbmonitor.dto.CrecimientoDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RegistroCrecimientoServiceTests {
    private final RegistroCrecimientoService service = new RegistroCrecimientoService();
    private final CrecimientoDto.Origen origen = origen("1", "10", 3, "SERVICIO_A");

    private CrecimientoDto.Origen origen(String dbid, String pdbDbid, int conId, String servicio) {
        return new CrecimientoDto.Origen(new BigDecimal(dbid), new BigDecimal(pdbDbid), conId,
                "FREE", servicio, "PDB" + conId);
    }

    private CrecimientoDto.Registro registro(int id, CrecimientoDto.Origen identidad, String total, String usado, String libre) {
        return new CrecimientoDto.Registro(BigDecimal.valueOf(id), LocalDateTime.of(2026, 10, 28, 12, id),
                identidad, new BigDecimal(total), new BigDecimal(usado), new BigDecimal(libre));
    }

    @Test
    void calculaVariacionEntreMedicionesConsecutivas() {
        var mediciones = service.obtenerMediciones(List.of(
                registro(2, origen, "1200", "600", "600"),
                registro(1, origen, "1000", "500", "500")), origen);
        assertEquals(2, mediciones.size());
        assertEquals(new BigDecimal("1200"), mediciones.getFirst().tamanoTotalBytes());
        assertEquals(new BigDecimal("100"), mediciones.getFirst().variacionBytes());
        assertEquals(new BigDecimal("20.00"), mediciones.getFirst().variacionPorcentual());
        assertNull(mediciones.getLast().variacionBytes());
    }

    @Test
    void valoresNulosNoRompenElServicio() {
        assertTrue(service.obtenerMediciones(null, origen).isEmpty());
        assertTrue(service.obtenerMediciones(List.of(), null).isEmpty());
    }

    @Test
    void basesYContenedoresDiferentesNoSeComparan() {
        var mediciones = service.obtenerMediciones(List.of(
                registro(1, origen, "1000", "500", "500"),
                registro(2, origen("2", "10", 3, "OTRO"), "9000", "8000", "1000"),
                registro(3, origen("1", "20", 4, "OTRO_PDB"), "9000", "8000", "1000"),
                registro(4, origen, "1200", "600", "600")), origen);
        assertEquals(2, mediciones.size());
        assertEquals(new BigDecimal("100"), mediciones.getFirst().variacionBytes());
    }

    @Test
    void cambiarServicioNoDivideLaHistoriaDeLaMismaBase() {
        var mediciones = service.obtenerMediciones(List.of(
                registro(1, origen, "1000", "500", "500"),
                registro(2, origen("1", "10", 3, "SERVICIO_B"), "1200", "600", "600")), origen);
        assertEquals(2, mediciones.size());
        assertEquals(new BigDecimal("20.00"), mediciones.getFirst().variacionPorcentual());
    }

    @Test
    void registrosSinIdentidadOCorruptosNoSeMuestran() {
        var incompleto = new CrecimientoDto.Registro(BigDecimal.ONE, null, origen,
                BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ONE);
        assertTrue(service.obtenerMediciones(Arrays.asList(incompleto, null,
                registro(2, null, "1000", "500", "500")), origen).isEmpty());
    }

    @Test
    void baseInicialCeroNoInventaPorcentaje() {
        var mediciones = service.obtenerMediciones(List.of(
                registro(1, origen, "1000", "0", "1000"),
                registro(2, origen, "1000", "100", "900")), origen);
        assertEquals(new BigDecimal("100"), mediciones.getFirst().variacionBytes());
        assertNull(mediciones.getFirst().variacionPorcentual());
    }

    @Test
    void noCargaNiModificaElJsonHeredado(@TempDir Path temporal) throws Exception {
        var archivo = temporal.resolve("historial_almacenamiento.json");
        var contenido = "[{\"fecha\":\"2026-01-01\",\"usadoBytes\":500}]";
        Files.writeString(archivo, contenido);
        assertTrue(service.obtenerMediciones(List.of(), origen).isEmpty());
        assertEquals(contenido, Files.readString(archivo));
    }
}