package cr.ac.una.dbmonitor.repository;

import cr.ac.una.dbmonitor.dto.AuditoriaDto;
import cr.ac.una.dbmonitor.dto.FiltrosAuditoria;
import cr.ac.una.dbmonitor.service.ConexionOracleService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Consultas de auditoría sobre el diccionario de datos de Oracle (vistas DBA_* y V$PWFILE_USERS).
 * Todas son de solo lectura y requieren SELECT_CATALOG_ROLE o SELECT ANY DICTIONARY.
 * Los valores ingresados por el usuario siempre viajan como parámetros enlazados.
 */
@Repository
public class AuditoriaRepository {
    static final String VISTA_CONTEXTO = "USERENV";
    static final String VISTA_RESUMEN = "DBA_USERS/DBA_ROLES/DBA_OBJECTS";
    static final String VISTA_USUARIOS = "DBA_USERS";
    static final String VISTA_CUENTAS_ADMINISTRATIVAS = "V$PWFILE_USERS";
    static final String VISTA_ROLES = "DBA_ROLES";
    static final String VISTA_ASIGNACIONES = "DBA_ROLE_PRIVS";
    static final String VISTA_PRIVILEGIOS_SISTEMA = "DBA_SYS_PRIVS";
    static final String VISTA_PRIVILEGIOS_OBJETO = "DBA_TAB_PRIVS";
    static final String VISTA_OBJETOS = "DBA_OBJECTS";
    static final String VISTA_ERRORES = "DBA_ERRORS";
    static final String VISTA_DEPENDENCIAS = "DBA_DEPENDENCIES";
    static final String VISTA_INDICES = "DBA_INDEXES";

    private static final String CONTEXTO = "SELECT SYS_CONTEXT('USERENV', 'CON_NAME') AS CONTENEDOR, "
            + "SYS_CONTEXT('USERENV', 'CON_ID') AS ID, SYS_CONTEXT('USERENV', 'SESSION_USER') AS SESION FROM DUAL";

    private static final String RESUMEN = """
            SELECT
                (SELECT COUNT(*) FROM DBA_USERS) AS USUARIOS,
                (SELECT COUNT(*) FROM DBA_USERS WHERE ORACLE_MAINTAINED = 'N') AS USUARIOS_ORGANIZACION,
                (SELECT COUNT(*) FROM DBA_USERS
                  WHERE ACCOUNT_STATUS LIKE '%LOCKED%' OR ACCOUNT_STATUS LIKE '%EXPIRED%') AS CUENTAS_RESTRINGIDAS,
                (SELECT COUNT(*) FROM DBA_ROLES) AS ROLES,
                (SELECT COUNT(*) FROM DBA_OBJECTS WHERE STATUS = 'INVALID') AS OBJETOS_INVALIDOS
            FROM DUAL
            """;

    private static final String USUARIOS = """
            SELECT U.USERNAME, U.ACCOUNT_STATUS, U.CREATED, U.LOCK_DATE, U.EXPIRY_DATE, U.DEFAULT_TABLESPACE,
                   U.PROFILE, U.AUTHENTICATION_TYPE, U.COMMON, U.ORACLE_MAINTAINED, U.LAST_LOGIN,
                   (SELECT COUNT(*) FROM DBA_ROLE_PRIVS R WHERE R.GRANTEE = U.USERNAME) AS ROLES_DIRECTOS,
                   (SELECT COUNT(*) FROM DBA_SYS_PRIVS S WHERE S.GRANTEE = U.USERNAME) AS PRIVILEGIOS_DIRECTOS,
                   COUNT(*) OVER () AS TOTAL
            FROM DBA_USERS U
            WHERE 1 = 1""";

    private static final String CUENTAS_ADMINISTRATIVAS = """
            SELECT USERNAME, SYSDBA, SYSOPER, SYSASM, SYSBACKUP, SYSDG, SYSKM,
                   ACCOUNT_STATUS, AUTHENTICATION_TYPE, COMMON, LAST_LOGIN
            FROM V$PWFILE_USERS
            ORDER BY USERNAME
            """;

    private static final String ROLES = """
            SELECT R.ROLE, R.AUTHENTICATION_TYPE, R.COMMON, R.ORACLE_MAINTAINED,
                   (SELECT COUNT(*) FROM DBA_ROLE_PRIVS P JOIN DBA_USERS U ON U.USERNAME = P.GRANTEE
                     WHERE P.GRANTED_ROLE = R.ROLE) AS USUARIOS_ASIGNADOS,
                   (SELECT COUNT(*) FROM DBA_ROLE_PRIVS P JOIN DBA_ROLES X ON X.ROLE = P.GRANTEE
                     WHERE P.GRANTED_ROLE = R.ROLE) AS ROLES_ASIGNADOS,
                   COUNT(*) OVER () AS TOTAL
            FROM DBA_ROLES R
            WHERE 1 = 1""";

    private static final String ASIGNACIONES = """
            SELECT P.GRANTEE, P.GRANTED_ROLE, P.ADMIN_OPTION, P.DEFAULT_ROLE, P.COMMON,
                   CASE WHEN U.USERNAME IS NOT NULL THEN 'USUARIO'
                        WHEN R.ROLE IS NOT NULL THEN 'ROL'
                        ELSE 'OTRO' END AS TIPO_BENEFICIARIO,
                   COUNT(*) OVER () AS TOTAL
            FROM DBA_ROLE_PRIVS P
            LEFT JOIN DBA_USERS U ON U.USERNAME = P.GRANTEE
            LEFT JOIN DBA_ROLES R ON R.ROLE = P.GRANTEE
            WHERE 1 = 1""";

    private static final String BENEFICIARIOS = """
            SELECT USERNAME AS NOMBRE, 'USUARIO' AS TIPO, ORACLE_MAINTAINED FROM DBA_USERS
            UNION ALL
            SELECT ROLE, 'ROL', ORACLE_MAINTAINED FROM DBA_ROLES
            ORDER BY 2 DESC, 1
            """;

    // Recorre la jerarquía de roles (roles otorgados a roles) y conserva el camino más corto a cada rol.
    private static final String ROLES_EFECTIVOS = """
            SELECT ROL, NIVEL, RUTA, ADMIN_OPTION, DEFAULT_ROLE FROM (
                SELECT GRANTED_ROLE AS ROL, LEVEL AS NIVEL, SYS_CONNECT_BY_PATH(GRANTED_ROLE, ' > ') AS RUTA,
                       ADMIN_OPTION, DEFAULT_ROLE,
                       ROW_NUMBER() OVER (PARTITION BY GRANTED_ROLE ORDER BY LEVEL) AS FILA
                FROM DBA_ROLE_PRIVS
                START WITH GRANTEE = ?
                CONNECT BY NOCYCLE PRIOR GRANTED_ROLE = GRANTEE)
            WHERE FILA = 1
            ORDER BY NIVEL, ROL
            """;

    private static final String ROLES_DEL_BENEFICIARIO = "SELECT GRANTED_ROLE FROM DBA_ROLE_PRIVS "
            + "START WITH GRANTEE = ? CONNECT BY NOCYCLE PRIOR GRANTED_ROLE = GRANTEE";

    private static final String PRIVILEGIOS_SISTEMA = """
            SELECT P.PRIVILEGE, P.GRANTEE, P.ADMIN_OPTION,
                   COUNT(*) OVER () AS TOTAL,
                   COUNT(DISTINCT CASE WHEN P.PRIVILEGE LIKE '% ANY %' OR P.PRIVILEGE LIKE '% ANY'
                                       THEN P.PRIVILEGE END) OVER () AS PRIVILEGIOS_ANY
            FROM DBA_SYS_PRIVS P
            WHERE""";

    private static final String PRIVILEGIOS_OBJETO = """
            SELECT P.PRIVILEGE, P.OWNER, P.TABLE_NAME, P.TYPE, P.GRANTEE, P.GRANTOR, P.GRANTABLE,
                   COUNT(*) OVER () AS TOTAL
            FROM DBA_TAB_PRIVS P
            WHERE""";

    private static final String OBJETOS_INVALIDOS = """
            SELECT O.OWNER, O.OBJECT_NAME, O.OBJECT_TYPE, O.STATUS, O.LAST_DDL_TIME,
                   (SELECT COUNT(*) FROM DBA_ERRORS E
                     WHERE E.OWNER = O.OWNER AND E.NAME = O.OBJECT_NAME AND E.TYPE = O.OBJECT_TYPE
                       AND E.ATTRIBUTE = 'ERROR') AS ERRORES,
                   COUNT(*) OVER () AS TOTAL
            FROM DBA_OBJECTS O
            WHERE O.STATUS = 'INVALID'""";

    private static final String ERRORES = """
            SELECT E.OWNER, E.NAME, E.TYPE, E.LINE, E.POSITION, E.TEXT, E.ATTRIBUTE,
                   COUNT(*) OVER () AS TOTAL
            FROM DBA_ERRORS E
            WHERE 1 = 1""";

    // Objetos que dependen de otro objeto inválido: el problema del referido se propaga al dependiente.
    private static final String DEPENDENCIAS = """
            SELECT D.OWNER, D.NAME, D.TYPE, O.STATUS, D.REFERENCED_OWNER, D.REFERENCED_NAME, D.REFERENCED_TYPE,
                   COUNT(*) OVER () AS TOTAL
            FROM DBA_DEPENDENCIES D
            JOIN DBA_OBJECTS R ON R.OWNER = D.REFERENCED_OWNER AND R.OBJECT_NAME = D.REFERENCED_NAME
                              AND R.OBJECT_TYPE = D.REFERENCED_TYPE
            LEFT JOIN DBA_OBJECTS O ON O.OWNER = D.OWNER AND O.OBJECT_NAME = D.NAME AND O.OBJECT_TYPE = D.TYPE
            WHERE R.STATUS = 'INVALID'""";

    private static final String INDICES_INUTILIZABLES = """
            SELECT X.OWNER, X.INDEX_NAME, X.PARTICION, X.TABLE_OWNER, X.TABLE_NAME, COUNT(*) OVER () AS TOTAL
            FROM (
                SELECT I.OWNER, I.INDEX_NAME, CAST(NULL AS VARCHAR2(128)) AS PARTICION, I.TABLE_OWNER, I.TABLE_NAME
                FROM DBA_INDEXES I WHERE I.STATUS = 'UNUSABLE'
                UNION ALL
                SELECT P.INDEX_OWNER, P.INDEX_NAME, P.PARTITION_NAME, I.TABLE_OWNER, I.TABLE_NAME
                FROM DBA_IND_PARTITIONS P
                JOIN DBA_INDEXES I ON I.OWNER = P.INDEX_OWNER AND I.INDEX_NAME = P.INDEX_NAME
                WHERE P.STATUS = 'UNUSABLE'
                UNION ALL
                SELECT S.INDEX_OWNER, S.INDEX_NAME, S.SUBPARTITION_NAME, I.TABLE_OWNER, I.TABLE_NAME
                FROM DBA_IND_SUBPARTITIONS S
                JOIN DBA_INDEXES I ON I.OWNER = S.INDEX_OWNER AND I.INDEX_NAME = S.INDEX_NAME
                WHERE S.STATUS = 'UNUSABLE'
            ) X
            WHERE 1 = 1""";

    private static final String PROPIETARIO_SEGUN_ORIGEN = " IN (SELECT USERNAME FROM DBA_USERS WHERE ORACLE_MAINTAINED = ?)";
    private static final List<String> PRIVILEGIOS_ADMINISTRATIVOS =
            List.of("SYSDBA", "SYSOPER", "SYSASM", "SYSBACKUP", "SYSDG", "SYSKM");

    private final ConexionOracleService conexion;

    public AuditoriaRepository(ConexionOracleService conexion) {
        this.conexion = conexion;
    }

    public AuditoriaDto consultar(FiltrosAuditoria filtros) {
        JdbcTemplate jdbc;
        try {
            jdbc = conexion.obtenerJdbcTemplate();
        } catch (IllegalStateException ex) {
            return new AuditoriaDto(filtros, null, null, null, null, null, null,
                    List.of("No hay conexión configurada. Conecte una instancia Oracle."));
        }
        var avisos = new ArrayList<String>();
        var contexto = ConsultasOracle.unica(ConsultasOracle.leerTolerandoVacio(VISTA_CONTEXTO,
                () -> jdbc.query(CONTEXTO, (rs, row) -> new AuditoriaDto.Contexto(rs.getString("CONTENEDOR"),
                        rs.getInt("ID") > 1, rs.getString("SESION"))), avisos), VISTA_CONTEXTO, avisos);
        var resumen = ConsultasOracle.unica(ConsultasOracle.leerTolerandoVacio(VISTA_RESUMEN,
                () -> jdbc.query(RESUMEN, (rs, row) -> resumen(rs)), avisos), VISTA_RESUMEN, avisos);
        return switch (filtros.vista()) {
            case USUARIOS -> new AuditoriaDto(filtros, contexto, resumen, usuarios(jdbc, filtros, avisos),
                    null, null, null, avisos);
            case ROLES -> new AuditoriaDto(filtros, contexto, resumen, null, roles(jdbc, filtros, avisos),
                    null, null, avisos);
            case PRIVILEGIOS -> new AuditoriaDto(filtros, contexto, resumen, null, null,
                    privilegios(jdbc, filtros, contexto, avisos), null, avisos);
            case OBJETOS -> new AuditoriaDto(filtros, contexto, resumen, null, null, null,
                    objetos(jdbc, filtros, avisos), avisos);
        };
    }

    private AuditoriaDto.UsuariosAuditoria usuarios(JdbcTemplate jdbc, FiltrosAuditoria filtros, List<String> avisos) {
        var sentencia = new Sentencia(USUARIOS)
                .origen("U.ORACLE_MAINTAINED = ?", filtros)
                .texto(filtros, "U.USERNAME")
                .ordenar("U.ORACLE_MAINTAINED, U.USERNAME", filtros.limite());
        var usuarios = listado(jdbc, VISTA_USUARIOS, sentencia, (rs, row) -> new AuditoriaDto.Usuario(
                rs.getString("USERNAME"), rs.getString("ACCOUNT_STATUS"), fecha(rs, "CREATED"),
                fecha(rs, "LOCK_DATE"), fecha(rs, "EXPIRY_DATE"), rs.getString("DEFAULT_TABLESPACE"),
                rs.getString("PROFILE"), rs.getString("AUTHENTICATION_TYPE"), si(rs, "COMMON"),
                "Y".equals(rs.getString("ORACLE_MAINTAINED")), fecha(rs, "LAST_LOGIN"),
                rs.getInt("ROLES_DIRECTOS"), rs.getInt("PRIVILEGIOS_DIRECTOS")), avisos);
        var cuentas = ConsultasOracle.leerTolerandoVacio(VISTA_CUENTAS_ADMINISTRATIVAS,
                () -> jdbc.query(CUENTAS_ADMINISTRATIVAS, (rs, row) -> cuentaAdministrativa(rs)), avisos);
        return new AuditoriaDto.UsuariosAuditoria(usuarios, cuentas);
    }

    private AuditoriaDto.RolesAuditoria roles(JdbcTemplate jdbc, FiltrosAuditoria filtros, List<String> avisos) {
        var sentenciaRoles = new Sentencia(ROLES)
                .origen("R.ORACLE_MAINTAINED = ?", filtros)
                .texto(filtros, "R.ROLE")
                .ordenar("R.ORACLE_MAINTAINED, R.ROLE", filtros.limite());
        var roles = listado(jdbc, VISTA_ROLES, sentenciaRoles, (rs, row) -> new AuditoriaDto.Rol(
                rs.getString("ROLE"), rs.getString("AUTHENTICATION_TYPE"), si(rs, "COMMON"),
                "Y".equals(rs.getString("ORACLE_MAINTAINED")), rs.getInt("USUARIOS_ASIGNADOS"),
                rs.getInt("ROLES_ASIGNADOS")), avisos);
        var sentenciaAsignaciones = new Sentencia(ASIGNACIONES)
                .origen("NVL(U.ORACLE_MAINTAINED, R.ORACLE_MAINTAINED) = ?", filtros)
                .texto(filtros, "P.GRANTEE", "P.GRANTED_ROLE")
                .ordenar("CASE WHEN U.USERNAME IS NOT NULL THEN 0 ELSE 1 END, P.GRANTEE, P.GRANTED_ROLE",
                        filtros.limite());
        var asignaciones = listado(jdbc, VISTA_ASIGNACIONES, sentenciaAsignaciones,
                (rs, row) -> new AuditoriaDto.AsignacionRol(rs.getString("GRANTEE"),
                        rs.getString("TIPO_BENEFICIARIO"), rs.getString("GRANTED_ROLE"), si(rs, "ADMIN_OPTION"),
                        si(rs, "DEFAULT_ROLE"), si(rs, "COMMON")), avisos);
        return new AuditoriaDto.RolesAuditoria(roles, asignaciones);
    }

    private AuditoriaDto.PrivilegiosAuditoria privilegios(JdbcTemplate jdbc, FiltrosAuditoria filtros,
                                                          AuditoriaDto.Contexto contexto, List<String> avisos) {
        var beneficiarios = ConsultasOracle.leerTolerandoVacio(VISTA_USUARIOS + "/" + VISTA_ROLES,
                () -> jdbc.query(BENEFICIARIOS, (rs, row) -> new AuditoriaDto.Beneficiario(rs.getString("NOMBRE"),
                        rs.getString("TIPO"), "Y".equals(rs.getString("ORACLE_MAINTAINED")))), avisos);
        var nombre = filtros.beneficiario() != null ? filtros.beneficiario()
                : contexto != null ? contexto.usuarioSesion() : null;
        var seleccionado = seleccionar(beneficiarios, nombre);
        if (seleccionado == null) {
            return new AuditoriaDto.PrivilegiosAuditoria(beneficiarios, null, nombre, null, null, null, null,
                    List.of());
        }
        var beneficiario = seleccionado.nombre();
        var rolesEfectivos = ConsultasOracle.leerTolerandoVacio(VISTA_ASIGNACIONES,
                () -> jdbc.query(ROLES_EFECTIVOS, (rs, row) -> new AuditoriaDto.RolEfectivo(rs.getString("ROL"),
                        rs.getInt("NIVEL"), heredadoDe(rs.getString("RUTA")), si(rs, "ADMIN_OPTION"),
                        si(rs, "DEFAULT_ROLE")), beneficiario), avisos);

        var privilegiosAny = new Integer[1];
        var sentenciaSistema = alcance(new Sentencia(PRIVILEGIOS_SISTEMA), filtros, beneficiario)
                .texto(filtros, "P.PRIVILEGE")
                .ordenar("CASE WHEN P.GRANTEE = ? THEN 0 ELSE 1 END, P.PRIVILEGE, P.GRANTEE", filtros.limite(),
                        beneficiario);
        var sistema = listado(jdbc, VISTA_PRIVILEGIOS_SISTEMA, sentenciaSistema, (rs, row) -> {
            privilegiosAny[0] = rs.getInt("PRIVILEGIOS_ANY");
            var otorgadoA = rs.getString("GRANTEE");
            return new AuditoriaDto.PrivilegioSistema(rs.getString("PRIVILEGE"), otorgadoA,
                    beneficiario.equals(otorgadoA), si(rs, "ADMIN_OPTION"));
        }, avisos);
        if (sistema != null && sistema.filas().isEmpty()) {
            privilegiosAny[0] = 0;
        }

        var sentenciaObjeto = alcance(new Sentencia(PRIVILEGIOS_OBJETO), filtros, beneficiario)
                .texto(filtros, "P.OWNER", "P.TABLE_NAME", "P.PRIVILEGE")
                .ordenar("CASE WHEN P.GRANTEE = ? THEN 0 ELSE 1 END, P.OWNER, P.TABLE_NAME, P.PRIVILEGE, P.GRANTEE",
                        filtros.limite(), beneficiario);
        var objetos = listado(jdbc, VISTA_PRIVILEGIOS_OBJETO, sentenciaObjeto, (rs, row) -> {
            var otorgadoA = rs.getString("GRANTEE");
            return new AuditoriaDto.PrivilegioObjeto(rs.getString("PRIVILEGE"), rs.getString("OWNER"),
                    rs.getString("TABLE_NAME"), rs.getString("TYPE"), otorgadoA, rs.getString("GRANTOR"),
                    beneficiario.equals(otorgadoA), si(rs, "GRANTABLE"));
        }, avisos);
        return new AuditoriaDto.PrivilegiosAuditoria(beneficiarios, seleccionado, nombre, rolesEfectivos,
                sistema, privilegiosAny[0], objetos, List.of());
    }

    private AuditoriaDto.Beneficiario seleccionar(List<AuditoriaDto.Beneficiario> beneficiarios, String nombre) {
        if (nombre == null) {
            return null;
        }
        if (beneficiarios == null) {
            // Sin acceso a la lista no se puede validar el nombre; se consulta igual con parámetros enlazados.
            return new AuditoriaDto.Beneficiario(nombre, null, false);
        }
        return beneficiarios.stream().filter(b -> b.nombre().equals(nombre)).findFirst().orElse(null);
    }

    private Sentencia alcance(Sentencia sentencia, FiltrosAuditoria filtros, String beneficiario) {
        return switch (filtros.origenPrivilegio()) {
            case TODOS -> sentencia.condicion(" (P.GRANTEE = ? OR P.GRANTEE IN (" + ROLES_DEL_BENEFICIARIO + "))",
                    beneficiario, beneficiario);
            case DIRECTOS -> sentencia.condicion(" P.GRANTEE = ?", beneficiario);
            case ROLES -> sentencia.condicion(" P.GRANTEE IN (" + ROLES_DEL_BENEFICIARIO + ")", beneficiario);
        };
    }

    private AuditoriaDto.ObjetosAuditoria objetos(JdbcTemplate jdbc, FiltrosAuditoria filtros, List<String> avisos) {
        var sentenciaInvalidos = new Sentencia(OBJETOS_INVALIDOS)
                .origen("O.ORACLE_MAINTAINED = ?", filtros)
                .texto(filtros, "O.OWNER", "O.OBJECT_NAME")
                .ordenar("O.OWNER, O.OBJECT_TYPE, O.OBJECT_NAME", filtros.limite());
        var invalidos = listado(jdbc, VISTA_OBJETOS, sentenciaInvalidos, (rs, row) -> new AuditoriaDto.ObjetoInvalido(
                rs.getString("OWNER"), rs.getString("OBJECT_NAME"), rs.getString("OBJECT_TYPE"),
                rs.getString("STATUS"), fecha(rs, "LAST_DDL_TIME"), rs.getInt("ERRORES")), avisos);

        var sentenciaErrores = new Sentencia(ERRORES)
                .origen("E.OWNER" + PROPIETARIO_SEGUN_ORIGEN, filtros)
                .texto(filtros, "E.OWNER", "E.NAME")
                .ordenar("E.OWNER, E.NAME, E.TYPE, E.SEQUENCE", filtros.limite());
        var errores = listado(jdbc, VISTA_ERRORES, sentenciaErrores, (rs, row) -> new AuditoriaDto.ErrorCompilacion(
                rs.getString("OWNER"), rs.getString("NAME"), rs.getString("TYPE"), rs.getInt("LINE"),
                rs.getInt("POSITION"), rs.getString("TEXT"), rs.getString("ATTRIBUTE")), avisos);

        var sentenciaDependencias = new Sentencia(DEPENDENCIAS)
                .origen("R.ORACLE_MAINTAINED = ?", filtros)
                .texto(filtros, "D.OWNER", "D.NAME", "D.REFERENCED_NAME")
                .ordenar("D.REFERENCED_OWNER, D.REFERENCED_NAME, D.OWNER, D.NAME", filtros.limite());
        var dependencias = listado(jdbc, VISTA_DEPENDENCIAS, sentenciaDependencias,
                (rs, row) -> new AuditoriaDto.DependenciaProblematica(rs.getString("OWNER"), rs.getString("NAME"),
                        rs.getString("TYPE"), rs.getString("STATUS"), rs.getString("REFERENCED_OWNER"),
                        rs.getString("REFERENCED_NAME"), rs.getString("REFERENCED_TYPE")), avisos);

        var sentenciaIndices = new Sentencia(INDICES_INUTILIZABLES)
                .origen("X.OWNER" + PROPIETARIO_SEGUN_ORIGEN, filtros)
                .texto(filtros, "X.OWNER", "X.INDEX_NAME", "X.TABLE_NAME")
                .ordenar("X.OWNER, X.INDEX_NAME, X.PARTICION", filtros.limite());
        var indices = listado(jdbc, VISTA_INDICES, sentenciaIndices, (rs, row) -> new AuditoriaDto.IndiceInutilizable(
                rs.getString("OWNER"), rs.getString("INDEX_NAME"), rs.getString("PARTICION"),
                rs.getString("TABLE_OWNER"), rs.getString("TABLE_NAME")), avisos);
        return new AuditoriaDto.ObjetosAuditoria(invalidos, errores, dependencias, indices);
    }

    private <T> AuditoriaDto.Listado<T> listado(JdbcTemplate jdbc, String vista, Sentencia sentencia,
                                                RowMapper<T> mapper, List<String> avisos) {
        var total = new int[1];
        var filas = ConsultasOracle.leerTolerandoVacio(vista, () -> jdbc.query(sentencia.sql(), (rs, row) -> {
            total[0] = rs.getInt("TOTAL");
            return mapper.mapRow(rs, row);
        }, sentencia.parametros()), avisos);
        if (filas == null) {
            return null;
        }
        return new AuditoriaDto.Listado<>(filas, Math.max(total[0], filas.size()));
    }

    private AuditoriaDto.Resumen resumen(ResultSet rs) throws SQLException {
        return new AuditoriaDto.Resumen(entero(rs, "USUARIOS"), entero(rs, "USUARIOS_ORGANIZACION"),
                entero(rs, "CUENTAS_RESTRINGIDAS"), entero(rs, "ROLES"), entero(rs, "OBJETOS_INVALIDOS"));
    }

    private AuditoriaDto.CuentaAdministrativa cuentaAdministrativa(ResultSet rs) throws SQLException {
        var privilegios = new ArrayList<String>();
        for (var privilegio : PRIVILEGIOS_ADMINISTRATIVOS) {
            if ("TRUE".equals(rs.getString(privilegio))) {
                privilegios.add(privilegio);
            }
        }
        return new AuditoriaDto.CuentaAdministrativa(rs.getString("USERNAME"), privilegios,
                rs.getString("ACCOUNT_STATUS"), rs.getString("AUTHENTICATION_TYPE"), si(rs, "COMMON"),
                fecha(rs, "LAST_LOGIN"));
    }

    /** Convierte " > DBA > SELECT_CATALOG_ROLE" en "DBA": los roles intermedios por los que se hereda. */
    static String heredadoDe(String ruta) {
        if (ruta == null) {
            return null;
        }
        var roles = Arrays.stream(ruta.split(" > ")).filter(r -> !r.isBlank()).toList();
        if (roles.size() <= 1) {
            return null;
        }
        return String.join(" → ", roles.subList(0, roles.size() - 1));
    }

    private Integer entero(ResultSet rs, String columna) throws SQLException {
        return rs.getObject(columna, Integer.class);
    }

    private boolean si(ResultSet rs, String columna) throws SQLException {
        return "YES".equals(rs.getString(columna));
    }

    private LocalDateTime fecha(ResultSet rs, String columna) throws SQLException {
        var valor = rs.getTimestamp(columna);
        return valor == null ? null : valor.toLocalDateTime();
    }

    /** Arma la sentencia agregando solo fragmentos fijos; los valores variables quedan como parámetros. */
    static final class Sentencia {
        private final StringBuilder sql;
        private final List<Object> parametros = new ArrayList<>();

        Sentencia(String base) {
            this.sql = new StringBuilder(base);
        }

        Sentencia condicion(String fragmento, Object... valores) {
            sql.append(fragmento);
            parametros.addAll(List.of(valores));
            return this;
        }

        Sentencia origen(String condicion, FiltrosAuditoria filtros) {
            return filtros.filtraPorOrigen()
                    ? condicion(" AND " + condicion, filtros.origen().valorOracle()) : this;
        }

        Sentencia texto(FiltrosAuditoria filtros, String... columnas) {
            if (!filtros.filtraPorTexto()) {
                return this;
            }
            var condiciones = Arrays.stream(columnas).map(c -> "UPPER(" + c + ") LIKE ? ESCAPE '\\'").toList();
            sql.append(" AND (").append(String.join(" OR ", condiciones)).append(')');
            for (var ignored : columnas) {
                parametros.add(filtros.patronBusqueda());
            }
            return this;
        }

        Sentencia ordenar(String orden, int limite, Object... valoresOrden) {
            sql.append(" ORDER BY ").append(orden).append(" FETCH FIRST ? ROWS ONLY");
            parametros.addAll(List.of(valoresOrden));
            parametros.add(limite);
            return this;
        }

        String sql() {
            return sql.toString();
        }

        Object[] parametros() {
            return parametros.toArray();
        }
    }
}
