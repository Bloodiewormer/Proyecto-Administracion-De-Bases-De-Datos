package cr.ac.una.dbmonitor.service;

import cr.ac.una.dbmonitor.dto.AuditoriaDto;
import cr.ac.una.dbmonitor.dto.FiltroOrigen;
import cr.ac.una.dbmonitor.dto.FiltrosAuditoria;
import cr.ac.una.dbmonitor.dto.OrigenPrivilegio;
import cr.ac.una.dbmonitor.dto.VistaAuditoria;
import cr.ac.una.dbmonitor.repository.AuditoriaRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class AuditoriaService {
    private static final int LIMITE_MINIMO = 10;
    private static final int LIMITE_MAXIMO = 500;
    private static final int LIMITE_POR_DEFECTO = 200;
    // Los identificadores de Oracle admiten como máximo 128 caracteres.
    private static final int LONGITUD_MAXIMA_TEXTO = 128;
    private static final String ROL_DBA = "DBA";

    private final AuditoriaRepository repository;

    public AuditoriaService(AuditoriaRepository repository) {
        this.repository = repository;
    }

    public AuditoriaDto obtenerAuditoria(VistaAuditoria vista, FiltroOrigen origen, String busqueda, Integer limite,
                                         String beneficiario, OrigenPrivilegio origenPrivilegio) {
        var filtros = new FiltrosAuditoria(vista, origen, texto(busqueda), limitar(limite), texto(beneficiario),
                origenPrivilegio);
        var datos = repository.consultar(filtros);
        if (datos.privilegios() == null) {
            return datos;
        }
        var privilegios = datos.privilegios();
        return datos.conPrivilegios(privilegios.conPresentacion(visibles(privilegios, origen),
                observaciones(privilegios)));
    }

    private int limitar(Integer limite) {
        return limite == null ? LIMITE_POR_DEFECTO : Math.clamp(limite, LIMITE_MINIMO, LIMITE_MAXIMO);
    }

    private String texto(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        var limpio = valor.trim();
        return limpio.length() > LONGITUD_MAXIMA_TEXTO ? limpio.substring(0, LONGITUD_MAXIMA_TEXTO) : limpio;
    }

    /** Lista de usuarios y roles para el selector según el origen elegido, conservando siempre el seleccionado. */
    private List<AuditoriaDto.Beneficiario> visibles(AuditoriaDto.PrivilegiosAuditoria privilegios, FiltroOrigen origen) {
        if (privilegios.beneficiarios() == null) {
            return null;
        }
        var seleccionado = privilegios.seleccionado();
        return privilegios.beneficiarios().stream()
                .filter(b -> origen.incluye(b.mantenidoPorOracle())
                        || (seleccionado != null && b.nombre().equals(seleccionado.nombre())))
                .toList();
    }

    List<String> observaciones(AuditoriaDto.PrivilegiosAuditoria privilegios) {
        if (privilegios.seleccionado() == null) {
            return List.of();
        }
        var observaciones = new ArrayList<String>();
        var roles = privilegios.rolesEfectivos();
        var sistema = privilegios.sistema();
        if (roles != null) {
            roles.stream().filter(r -> ROL_DBA.equals(r.rol())).findFirst().ifPresent(r -> observaciones.add(
                    (r.directo() ? "Tiene asignado directamente el rol DBA" : "Hereda el rol DBA mediante " + r.ruta())
                            + ": dispone de privilegios para administrar toda la base de datos."));
            var noPredeterminados = roles.stream().filter(r -> r.directo() && !r.predeterminado()).count();
            if (noPredeterminados > 0) {
                observaciones.add(noPredeterminados + " rol(es) asignado(s) no son predeterminados: solo se activan "
                        + "si la sesión ejecuta SET ROLE.");
            }
        }
        if (privilegios.privilegiosAny() != null && privilegios.privilegiosAny() > 0) {
            observaciones.add("Posee " + privilegios.privilegiosAny() + " privilegio(s) de sistema «ANY» distintos, "
                    + "que permiten actuar sobre objetos de cualquier esquema.");
        }
        long conAdministracion = 0;
        if (sistema != null) {
            conAdministracion += sistema.filas().stream().filter(p -> p.directo() && p.conAdministracion()).count();
        }
        if (roles != null) {
            conAdministracion += roles.stream().filter(r -> r.directo() && r.conAdministracion()).count();
        }
        if (conAdministracion > 0) {
            observaciones.add("Puede otorgar a otras cuentas " + conAdministracion + " privilegio(s) o rol(es) "
                    + "recibidos directamente con opción de administración (ADMIN OPTION).");
        }
        if (observaciones.isEmpty() && roles != null && sistema != null) {
            observaciones.add("No se identificaron privilegios de alto impacto (rol DBA, privilegios «ANY» u opción "
                    + "de administración) en la información consultada.");
        }
        return observaciones;
    }
}
