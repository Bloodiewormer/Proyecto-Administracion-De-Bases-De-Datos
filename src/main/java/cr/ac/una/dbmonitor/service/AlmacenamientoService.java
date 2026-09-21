package cr.ac.una.dbmonitor.service;

import cr.ac.una.dbmonitor.dto.AlmacenamientoDto;
import cr.ac.una.dbmonitor.dto.ArchivoDatosDto;
import cr.ac.una.dbmonitor.dto.CrecimientoDto;
import cr.ac.una.dbmonitor.dto.FiltroTipoObjeto;
import cr.ac.una.dbmonitor.dto.ResumenAlmacenamientoDto;
import cr.ac.una.dbmonitor.dto.TablespaceDto;
import cr.ac.una.dbmonitor.repository.AlmacenamientoRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
public class AlmacenamientoService {

    private static final int TOP_MINIMO = 5;
    private static final int TOP_MAXIMO = 50;
    private static final int TOP_POR_DEFECTO = 10;

    private final AlmacenamientoRepository repository;
    private final RegistroCrecimientoService registroCrecimiento;

    public AlmacenamientoService(AlmacenamientoRepository repository,
                                 RegistroCrecimientoService registroCrecimiento) {
        this.repository = repository;
        this.registroCrecimiento = registroCrecimiento;
    }

    public AlmacenamientoDto obtenerAlmacenamiento(FiltroTipoObjeto filtro, Integer top) {
        var filtroAplicado = filtro == null ? FiltroTipoObjeto.TODOS : filtro;
        var limite = limitar(top);

        var raw = repository.consultar(filtroAplicado, limite);
        var avisos = new ArrayList<>(raw.avisos());

        var tablespaces = procesarTablespaces(raw.tablespaces(), avisos);
        var resumen = procesarResumen(raw.resumen(), tablespaces, raw.archivos(), avisos);
        var medicionesHistoricas = registroCrecimiento.obtenerMediciones();
        var crecimiento = new CrecimientoDto(medicionesHistoricas, raw.historicoAwr());

        return new AlmacenamientoDto(resumen, tablespaces, raw.archivos(), raw.objetos(),
                crecimiento, filtroAplicado, limite, avisos);
    }

    public void registrarMedicionActual() {
        var raw = repository.consultar(FiltroTipoObjeto.TODOS, 5);
        var tablespaces = procesarTablespaces(raw.tablespaces(), new ArrayList<>());
        var resumen = procesarResumen(raw.resumen(), tablespaces, raw.archivos(), new ArrayList<>());

        if (resumen != null) {
            registroCrecimiento.registrarMedicion(
                    resumen.tamanoTotalBytes(),
                    resumen.usadoBytes(),
                    resumen.libreBytes()
            );
        }
    }

    private int limitar(Integer top) {
        return top == null ? TOP_POR_DEFECTO : Math.clamp(top, TOP_MINIMO, TOP_MAXIMO);
    }

    private List<TablespaceDto> procesarTablespaces(List<TablespaceDto> lista, List<String> avisos) {
        if (lista == null) {
            return List.of();
        }
        return lista.stream().map(ts -> {
            var porcentaje = calcularPorcentaje(ts.bytesUsados(), ts.bytesTotales());
            return new TablespaceDto(
                    ts.nombre(),
                    ts.contenido(),
                    ts.estado(),
                    ts.gestionExtensiones(),
                    ts.gestionSegmentos(),
                    ts.bytesTotales(),
                    ts.bytesUsados(),
                    ts.bytesLibres(),
                    ts.bytesMaximos(),
                    porcentaje
            );
        }).toList();
    }

    private ResumenAlmacenamientoDto procesarResumen(ResumenAlmacenamientoDto rawResumen,
                                                    List<TablespaceDto> tablespaces,
                                                    List<ArchivoDatosDto> archivos,
                                                    List<String> avisos) {
        if (rawResumen == null && (tablespaces == null || tablespaces.isEmpty())) {
            return null;
        }

        var datafiles = (rawResumen != null && rawResumen.bytesDatafiles() != null)
                ? rawResumen.bytesDatafiles() : BigDecimal.ZERO;
        var tempfiles = (rawResumen != null && rawResumen.bytesTempfiles() != null)
                ? rawResumen.bytesTempfiles() : BigDecimal.ZERO;
        var redoLogs = (rawResumen != null && rawResumen.bytesRedoLogs() != null)
                ? rawResumen.bytesRedoLogs() : BigDecimal.ZERO;

        if (datafiles.signum() == 0 && tempfiles.signum() == 0 && tablespaces != null && !tablespaces.isEmpty()) {
            datafiles = tablespaces.stream()
                    .filter(ts -> !"TEMPORARY".equalsIgnoreCase(ts.contenido()))
                    .map(TablespaceDto::bytesTotales)
                    .filter(b -> b != null && b.signum() > 0)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            tempfiles = tablespaces.stream()
                    .filter(ts -> "TEMPORARY".equalsIgnoreCase(ts.contenido()))
                    .map(TablespaceDto::bytesTotales)
                    .filter(b -> b != null && b.signum() > 0)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        var tamanoTotal = datafiles.add(tempfiles).add(redoLogs);
        var usado = (rawResumen != null) ? rawResumen.usadoBytes() : null;

        var sumaUsadoTs = (tablespaces != null) ? tablespaces.stream()
                .map(TablespaceDto::bytesUsados)
                .filter(b -> b != null && b.signum() > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add) : BigDecimal.ZERO;

        if (usado == null || usado.signum() == 0) {
            if (sumaUsadoTs.signum() > 0) {
                usado = sumaUsadoTs;
            }
        }

        var sumaLibreTs = (tablespaces != null) ? tablespaces.stream()
                .map(TablespaceDto::bytesLibres)
                .filter(b -> b != null && b.signum() > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add) : BigDecimal.ZERO;

        BigDecimal libre = null;
        if (sumaLibreTs.signum() > 0) {
            libre = sumaLibreTs;
        } else if (usado != null) {
            var espacioArchivos = datafiles.add(tempfiles);
            if (espacioArchivos.compareTo(usado) >= 0) {
                libre = espacioArchivos.subtract(usado);
            } else if (tamanoTotal.compareTo(usado) >= 0) {
                libre = tamanoTotal.subtract(usado);
            } else {
                libre = BigDecimal.ZERO;
            }
        }

        var porcentajeUso = calcularPorcentaje(usado, tamanoTotal);

        var cantidadTs = (rawResumen != null && rawResumen.cantidadTablespaces() != null)
                ? rawResumen.cantidadTablespaces()
                : (tablespaces != null ? tablespaces.size() : 0);

        var cantidadArch = (rawResumen != null && rawResumen.cantidadArchivos() != null)
                ? rawResumen.cantidadArchivos()
                : (archivos != null ? archivos.size() : 0);

        return new ResumenAlmacenamientoDto(
                tamanoTotal,
                usado,
                libre,
                porcentajeUso,
                datafiles,
                tempfiles,
                redoLogs,
                cantidadTs,
                cantidadArch
        );
    }

    private BigDecimal calcularPorcentaje(BigDecimal parte, BigDecimal total) {
        if (parte == null || total == null || total.signum() <= 0 || parte.signum() < 0) {
            return BigDecimal.ZERO;
        }
        return parte.multiply(BigDecimal.valueOf(100)).divide(total, 2, RoundingMode.HALF_UP);
    }
}
