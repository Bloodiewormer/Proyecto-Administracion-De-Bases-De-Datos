package cr.ac.una.dbmonitor.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import cr.ac.una.dbmonitor.dto.CrecimientoDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class RegistroCrecimientoService {

    private static final Logger log = LoggerFactory.getLogger(RegistroCrecimientoService.class);
    private static final String ARCHIVO_HISTORIAL = "historial_almacenamiento.json";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final ObjectMapper objectMapper;
    private final List<RegistroPersistido> registrosEnMemoria = Collections.synchronizedList(new ArrayList<>());

    public record RegistroPersistido(String fecha, BigDecimal tamanoTotalBytes,
                                     BigDecimal usadoBytes, BigDecimal libreBytes) {}

    public RegistroCrecimientoService() {
        this.objectMapper = new ObjectMapper();
        cargarDesdeArchivo();
    }

    public synchronized void registrarMedicion(BigDecimal tamanoTotalBytes, BigDecimal usadoBytes, BigDecimal libreBytes) {
        if (tamanoTotalBytes == null && usadoBytes == null && libreBytes == null) {
            return;
        }
        var nuevo = new RegistroPersistido(LocalDateTime.now().format(FORMATTER),
                tamanoTotalBytes, usadoBytes, libreBytes);
        registrosEnMemoria.add(nuevo);
        guardarEnArchivo();
    }

    public synchronized List<CrecimientoDto.Medicion> obtenerMediciones() {
        var resultado = new ArrayList<CrecimientoDto.Medicion>();
        RegistroPersistido anterior = null;

        for (var reg : registrosEnMemoria) {
            LocalDateTime fecha;
            try {
                fecha = LocalDateTime.parse(reg.fecha(), FORMATTER);
            } catch (Exception ex) {
                fecha = LocalDateTime.now();
            }

            BigDecimal variacionBytes = BigDecimal.ZERO;
            BigDecimal variacionPorcentual = BigDecimal.ZERO;

            if (anterior != null && anterior.usadoBytes() != null && reg.usadoBytes() != null) {
                variacionBytes = reg.usadoBytes().subtract(anterior.usadoBytes());
                if (anterior.usadoBytes().signum() > 0) {
                    variacionPorcentual = variacionBytes
                            .multiply(BigDecimal.valueOf(100))
                            .divide(anterior.usadoBytes(), 2, RoundingMode.HALF_UP);
                }
            }

            resultado.add(new CrecimientoDto.Medicion(fecha, reg.tamanoTotalBytes(),
                    reg.usadoBytes(), reg.libreBytes(), variacionBytes, variacionPorcentual));
            anterior = reg;
        }

        // Devolver ordenado del más reciente al más antiguo para presentación cómoda
        return resultado.reversed();
    }

    private void cargarDesdeArchivo() {
        var archivo = new File(ARCHIVO_HISTORIAL);
        if (!archivo.exists()) {
            return;
        }
        try {
            List<RegistroPersistido> guardados = objectMapper.readValue(archivo, new TypeReference<>() {});
            if (guardados != null) {
                registrosEnMemoria.addAll(guardados);
            }
        } catch (IOException ex) {
            log.warn("No fue posible cargar el historial de almacenamiento previo: {}", ex.getMessage());
        }
    }

    private void guardarEnArchivo() {
        try {
            var archivo = new File(ARCHIVO_HISTORIAL);
            objectMapper.writeValue(archivo, new ArrayList<>(registrosEnMemoria));
        } catch (IOException ex) {
            log.warn("No fue posible guardar el historial de almacenamiento en archivo: {}", ex.getMessage());
        }
    }
}
