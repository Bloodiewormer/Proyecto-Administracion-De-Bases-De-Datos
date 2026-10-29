package cr.ac.una.dbmonitor.service;

import cr.ac.una.dbmonitor.dto.*;
import cr.ac.una.dbmonitor.repository.AlmacenamientoRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

@Service
public class AlmacenamientoService {
    private final AlmacenamientoRepository repository;
    private final RegistroCrecimientoService registroCrecimiento;

    public AlmacenamientoService(AlmacenamientoRepository repository, RegistroCrecimientoService registroCrecimiento) {
        this.repository = repository;
        this.registroCrecimiento = registroCrecimiento;
    }

    public AlmacenamientoDto obtenerAlmacenamiento(FiltroTipoObjeto filtro, Integer top) {
        var filtroAplicado = filtro == null ? FiltroTipoObjeto.TODOS : filtro;
        var limite = top == null ? 10 : Math.clamp(top, 5, 50);
        var raw = repository.consultar(filtroAplicado, limite);
        var avisos = new ArrayList<>(raw.avisos());
        var tablespaces = raw.tablespaces().stream().map(ts -> new TablespaceDto(ts.nombre(), ts.contenido(),
                ts.estado(), ts.gestionExtensiones(), ts.gestionSegmentos(), ts.bytesTotales(), ts.bytesUsados(),
                ts.bytesLibres(), ts.bytesMaximos(), ocupacionValida(ts)
                        ? porcentaje(ts.bytesUsados(), ts.bytesTotales()) : null)).toList();
        var resumen = resumen(raw.resumen(), tablespaces, raw.archivos(), avisos);
        var crecimiento = new CrecimientoDto(registroCrecimiento.obtenerMediciones(raw.registros(), raw.origen()),
                raw.historicoAwr(), raw.origen());
        return new AlmacenamientoDto(resumen, tablespaces, raw.archivos(), raw.objetos(), crecimiento,
                filtroAplicado, limite, avisos);
    }

    public void registrarMedicionActual() {
        try {
            var registro = repository.registrarMedicion();
            if (registro == null || !registro.valido()) {
                throw new IllegalStateException("Oracle no confirmó una medición completa y persistida.");
            }
        } catch (DataAccessException ex) {
            throw new IllegalStateException("No se pudo guardar y confirmar el historial en Oracle. "
                    + "Verifique la instalación del script de crecimiento, los permisos y la conexión.", ex);
        }
    }

    /** Un único denominador: archivos permanentes y temporales del contenedor, sin redo. */
    private ResumenAlmacenamientoDto resumen(ResumenAlmacenamientoDto raw, List<TablespaceDto> tablespaces,
                                            List<ArchivoDatosDto> archivos, List<String> avisos) {
        if (tablespaces.isEmpty()) {
            return null;
        }
        var total = sumar(tablespaces, TablespaceDto::bytesTotales);
        var usado = sumar(tablespaces, TablespaceDto::bytesUsados);
        var libre = sumar(tablespaces, TablespaceDto::bytesLibres);
        if (tablespaces.stream().anyMatch(t -> !ocupacionValida(t))
                || total == null || usado == null || libre == null || usado.add(libre).compareTo(total) != 0) {
            avisos.add("Resumen de almacenamiento: ocupación incompleta o inconsistente; no se estima con ceros.");
            usado = null;
            libre = null;
        }
        var datafiles = sumar(tablespaces.stream().filter(t -> !"TEMPORARY".equals(t.contenido())).toList(),
                TablespaceDto::bytesTotales);
        var tempfiles = sumar(tablespaces.stream().filter(t -> "TEMPORARY".equals(t.contenido())).toList(),
                TablespaceDto::bytesTotales);
        return new ResumenAlmacenamientoDto(total, usado, libre, porcentaje(usado, total), datafiles, tempfiles,
                raw == null ? null : raw.bytesRedoLogs(), tablespaces.size(), archivos == null ? null : archivos.size());
    }

    private BigDecimal sumar(List<TablespaceDto> filas, Function<TablespaceDto, BigDecimal> valor) {
        if (filas.stream().anyMatch(f -> valor.apply(f) == null || valor.apply(f).signum() < 0)) {
            return null;
        }
        return filas.stream().map(valor).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private boolean ocupacionValida(TablespaceDto ts) {
        return ts.bytesTotales() != null && ts.bytesUsados() != null && ts.bytesLibres() != null
                && ts.bytesTotales().signum() >= 0 && ts.bytesUsados().signum() >= 0
                && ts.bytesLibres().signum() >= 0
                && ts.bytesUsados().add(ts.bytesLibres()).compareTo(ts.bytesTotales()) == 0;
    }

    private BigDecimal porcentaje(BigDecimal usado, BigDecimal total) {
        if (usado == null || total == null || total.signum() <= 0 || usado.signum() < 0
                || usado.compareTo(total) > 0) {
            return null;
        }
        return usado.multiply(BigDecimal.valueOf(100)).divide(total, 2, RoundingMode.HALF_UP);
    }
}
