package cr.ac.una.dbmonitor.repository;

import cr.ac.una.dbmonitor.dto.AuditoriaDto;
import cr.ac.una.dbmonitor.dto.FiltroOrigen;
import cr.ac.una.dbmonitor.dto.FiltrosAuditoria;
import cr.ac.una.dbmonitor.dto.OrigenPrivilegio;
import cr.ac.una.dbmonitor.dto.VistaAuditoria;
import cr.ac.una.dbmonitor.service.ConexionOracleService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuditoriaRepositoryTests {
    private final ConexionOracleService conexion = mock(ConexionOracleService.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final AuditoriaRepository repository = new AuditoriaRepository(conexion);

    private FiltrosAuditoria filtros(VistaAuditoria vista) {
        return new FiltrosAuditoria(vista, FiltroOrigen.TODOS, null, 50, null, OrigenPrivilegio.TODOS);
    }

    private void sinFilas() {
        when(conexion.obtenerJdbcTemplate()).thenReturn(jdbc);
        when(jdbc.query(anyString(), any(RowMapper.class))).thenReturn(List.of());
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
    }

    private void conBeneficiarioSystem() {
        sinFilas();
        when(jdbc.query(contains("SESSION_USER"), any(RowMapper.class)))
                .thenReturn(List.of(new AuditoriaDto.Contexto("CDB$ROOT", false, "SYSTEM")));
        when(jdbc.query(contains("UNION ALL\nSELECT ROLE"), any(RowMapper.class)))
                .thenReturn(List.of(new AuditoriaDto.Beneficiario("SYSTEM", "USUARIO", true)));
    }

    private BadSqlGrammarException sinPermiso() {
        return new BadSqlGrammarException("consulta", "SELECT", new SQLException("ORA-00942", "42000", 942));
    }

    @Test
    void sinConexionNoEjecutaConsultas() {
        when(conexion.obtenerJdbcTemplate()).thenThrow(new IllegalStateException("privado"));
        var datos = repository.consultar(filtros(VistaAuditoria.USUARIOS));
        assertNull(datos.contexto());
        assertNull(datos.usuarios());
        assertTrue(datos.avisos().getFirst().contains("No hay conexión"));
        verifyNoInteractions(jdbc);
    }

    @ParameterizedTest
    @EnumSource(VistaAuditoria.class)
    void soloConsultaLaPestanaSeleccionada(VistaAuditoria vista) {
        sinFilas();
        var datos = repository.consultar(filtros(vista));
        assertEquals(vista == VistaAuditoria.USUARIOS, datos.usuarios() != null);
        assertEquals(vista == VistaAuditoria.ROLES, datos.roles() != null);
        assertEquals(vista == VistaAuditoria.PRIVILEGIOS, datos.privilegios() != null);
        assertEquals(vista == VistaAuditoria.OBJETOS, datos.objetos() != null);
    }

    @Test
    void resultadosVaciosNoSeReportanComoError() {
        sinFilas();
        var objetos = repository.consultar(filtros(VistaAuditoria.OBJETOS));
        assertTrue(objetos.objetos().invalidos().filas().isEmpty());
        assertEquals(0, objetos.objetos().invalidos().total());
        assertTrue(objetos.objetos().errores().filas().isEmpty());
        assertTrue(objetos.objetos().dependencias().filas().isEmpty());
        assertTrue(objetos.objetos().indices().filas().isEmpty());
        assertTrue(objetos.avisos().isEmpty());
        var usuarios = repository.consultar(filtros(VistaAuditoria.USUARIOS));
        assertTrue(usuarios.usuarios().usuarios().filas().isEmpty());
        assertTrue(usuarios.usuarios().cuentasAdministrativas().isEmpty());
        assertTrue(usuarios.avisos().isEmpty());
    }

    @Test
    void faltaDePermisosSeDistingueDeLaAusenciaDeProblemas() {
        sinFilas();
        when(jdbc.query(contains("FROM DBA_OBJECTS O"), any(RowMapper.class), any(Object[].class)))
                .thenThrow(sinPermiso());
        var datos = repository.consultar(filtros(VistaAuditoria.OBJETOS));
        assertNull(datos.objetos().invalidos());
        assertNotNull(datos.objetos().errores());
        assertTrue(datos.avisos().contains("DBA_OBJECTS: vista no accesible o inexistente."));
    }

    @Test
    void losFiltrosViajanComoParametrosYLosComodinesSeEscapan() {
        sinFilas();
        var filtros = new FiltrosAuditoria(VistaAuditoria.USUARIOS, FiltroOrigen.ORGANIZACION, "app_1%' OR 1=1",
                25, null, OrigenPrivilegio.TODOS);
        repository.consultar(filtros);
        verify(jdbc).query(argThat((String sql) -> sql.contains("U.ORACLE_MAINTAINED = ?")
                        && sql.contains("UPPER(U.USERNAME) LIKE ? ESCAPE '\\'") && !sql.contains("OR 1=1")),
                any(RowMapper.class), eq("N"), eq("%APP\\_1\\%' OR 1=1%"), eq(25));
    }

    @Test
    void sinFiltrosNoAgregaCondicionesYSoloLimitaFilas() {
        sinFilas();
        repository.consultar(filtros(VistaAuditoria.ROLES));
        verify(jdbc).query(argThat((String sql) -> sql.contains("FROM DBA_ROLES R") && !sql.contains("LIKE")
                && sql.endsWith("FETCH FIRST ? ROWS ONLY")), any(RowMapper.class), eq(50));
    }

    @ParameterizedTest
    @CsvSource({"TODOS,3", "DIRECTOS,2", "ROLES,2"})
    void privilegiosSeConsultanParaElBeneficiarioSegunSuOrigen(OrigenPrivilegio origen, int veces) {
        conBeneficiarioSystem();
        var datos = repository.consultar(new FiltrosAuditoria(VistaAuditoria.PRIVILEGIOS, FiltroOrigen.TODOS, null,
                40, null, origen));
        assertEquals("SYSTEM", datos.privilegios().seleccionado().nombre());
        verify(jdbc).query(contains("START WITH GRANTEE = ?"), any(RowMapper.class), eq("SYSTEM"));
        var parametros = new Object[veces + 1];
        Arrays.fill(parametros, "SYSTEM");
        parametros[veces] = 40;
        verify(jdbc).query(contains("FROM DBA_SYS_PRIVS P"), any(RowMapper.class), aryEq(parametros));
        verify(jdbc).query(contains("FROM DBA_TAB_PRIVS P"), any(RowMapper.class), aryEq(parametros));
    }

    @Test
    void beneficiarioInexistenteNoEjecutaConsultasDePrivilegios() {
        conBeneficiarioSystem();
        var datos = repository.consultar(new FiltrosAuditoria(VistaAuditoria.PRIVILEGIOS, FiltroOrigen.TODOS, null,
                40, "NO_EXISTE", OrigenPrivilegio.TODOS));
        assertNull(datos.privilegios().seleccionado());
        assertEquals("NO_EXISTE", datos.privilegios().nombreSolicitado());
        verify(jdbc, never()).query(contains("FROM DBA_SYS_PRIVS P"), any(RowMapper.class), any(Object[].class));
    }

    @Test
    void mapeaUsuariosConElTotalAntesDelLimite() throws Exception {
        sinFilas();
        var rs = mock(ResultSet.class);
        var momento = LocalDateTime.of(2026, 10, 20, 8, 30, 0);
        when(rs.getString("USERNAME")).thenReturn("APP_VENTAS");
        when(rs.getString("ACCOUNT_STATUS")).thenReturn("LOCKED");
        when(rs.getTimestamp("CREATED")).thenReturn(Timestamp.valueOf(momento));
        when(rs.getString("AUTHENTICATION_TYPE")).thenReturn("PASSWORD");
        when(rs.getString("COMMON")).thenReturn("NO");
        when(rs.getString("ORACLE_MAINTAINED")).thenReturn("N");
        when(rs.getInt("ROLES_DIRECTOS")).thenReturn(2);
        when(rs.getInt("PRIVILEGIOS_DIRECTOS")).thenReturn(1);
        when(rs.getInt("TOTAL")).thenReturn(7);
        doAnswer(inv -> List.of(inv.<RowMapper<?>>getArgument(1).mapRow(rs, 0)))
                .when(jdbc).query(contains("FROM DBA_USERS U"), any(RowMapper.class), any(Object[].class));
        var usuarios = repository.consultar(filtros(VistaAuditoria.USUARIOS)).usuarios().usuarios();
        var usuario = usuarios.filas().getFirst();
        assertEquals("APP_VENTAS", usuario.nombre());
        assertEquals(momento, usuario.creacion());
        assertFalse(usuario.comun());
        assertFalse(usuario.mantenidoPorOracle());
        assertEquals(2, usuario.rolesDirectos());
        assertEquals(7, usuarios.total());
        assertTrue(usuarios.truncado());
    }

    @Test
    void mapeaLosPrivilegiosDelArchivoDeContrasenas() throws Exception {
        sinFilas();
        var rs = mock(ResultSet.class);
        when(rs.getString("USERNAME")).thenReturn("SYS");
        when(rs.getString("SYSDBA")).thenReturn("TRUE");
        when(rs.getString("SYSOPER")).thenReturn("TRUE");
        when(rs.getString("SYSBACKUP")).thenReturn("FALSE");
        when(rs.getString("COMMON")).thenReturn("YES");
        doAnswer(inv -> List.of(inv.<RowMapper<?>>getArgument(1).mapRow(rs, 0)))
                .when(jdbc).query(contains("FROM V$PWFILE_USERS"), any(RowMapper.class));
        var cuenta = repository.consultar(filtros(VistaAuditoria.USUARIOS)).usuarios().cuentasAdministrativas()
                .getFirst();
        assertEquals(List.of("SYSDBA", "SYSOPER"), cuenta.privilegios());
        assertTrue(cuenta.comun());
    }

    @Test
    void laRutaDeHerenciaOmiteElPropioRol() {
        assertNull(AuditoriaRepository.heredadoDe(" > DBA"));
        assertEquals("DBA", AuditoriaRepository.heredadoDe(" > DBA > SELECT_CATALOG_ROLE"));
        assertEquals("DBA → EXP_FULL_DATABASE",
                AuditoriaRepository.heredadoDe(" > DBA > EXP_FULL_DATABASE > SELECT_CATALOG_ROLE"));
        assertNull(AuditoriaRepository.heredadoDe(null));
    }
}
