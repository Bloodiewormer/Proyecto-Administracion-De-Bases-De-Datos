package cr.ac.una.dbmonitor.service;

import com.zaxxer.hikari.HikariDataSource;
import cr.ac.una.dbmonitor.dto.ConexionOracleDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.net.SocketTimeoutException;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;

@Service
public class ConexionOracleService {
    private static final int TIMEOUT_CONSULTA_SEGUNDOS = 5;

    private volatile HikariDataSource origenActivo;

    public synchronized void conectar(ConexionOracleDto form) {
        HikariDataSource candidato = crearDataSource(form);
        try (var connection = candidato.getConnection();
             var statement = connection.createStatement()) {
            statement.setQueryTimeout(TIMEOUT_CONSULTA_SEGUNDOS);
            try (var result = statement.executeQuery("SELECT 1 FROM DUAL")) {
                if (!result.next() || result.getInt(1) != 1) {
                    throw new IllegalStateException("Oracle no devolvió el resultado esperado. La configuración activa no se modificó.");
                }
            }
        } catch (SQLException ex) {
            cerrar(candidato);
            throw new IllegalStateException(mensajeError(ex));
        } catch (RuntimeException ex) {
            cerrar(candidato);
            throw ex;
        }
        var anterior = origenActivo;
        origenActivo = candidato;
        cerrar(anterior);
    }

    HikariDataSource crearDataSource(ConexionOracleDto form) {
        var origen = new HikariDataSource();
        origen.setPoolName("oracle-monitor");
        origen.setJdbcUrl("jdbc:oracle:thin:@//" + form.getHost().trim() + ":" + form.getPort()
                + "/" + form.getServiceName().trim());
        origen.setUsername(form.getUsername().trim());
        origen.setPassword(form.getPassword());
        origen.setMaximumPoolSize(5);
        origen.setMinimumIdle(1);
        origen.setConnectionTimeout(5000);
        origen.setValidationTimeout(3000);
        origen.setIdleTimeout(60000);
        origen.setMaxLifetime(900000);
        origen.addDataSourceProperty("oracle.net.CONNECT_TIMEOUT", "5000");
        origen.addDataSourceProperty("oracle.net.OUTBOUND_CONNECT_TIMEOUT", "5000");
        origen.addDataSourceProperty("oracle.jdbc.ReadTimeout", "10000");
        return origen;
    }

    private void cerrar(HikariDataSource origen) {
        if (origen != null) {
            origen.close();
        }
    }

    public JdbcTemplate obtenerJdbcTemplate() {
        var origen = origenActivo;
        if (origen == null) {
            throw new IllegalStateException("Primero configure una conexión Oracle.");
        }
        var template = new JdbcTemplate(origen);
        template.setQueryTimeout(TIMEOUT_CONSULTA_SEGUNDOS);
        return template;
    }

    String mensajeError(SQLException error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLTimeoutException || cause instanceof SocketTimeoutException) {
                return "Se agotó el tiempo de espera. Revise la disponibilidad de Oracle.";
            }
            if (cause instanceof SQLException sql) {
                switch (sql.getErrorCode()) {
                    case 1017:
                        return "Credenciales inválidas.";
                    case 12514, 12505:
                        return "No fue posible localizar el servicio Oracle especificado.";
                    case 28000:
                        return "La cuenta Oracle está bloqueada.";
                    case 28001:
                        return "La contraseña de la cuenta Oracle ha expirado.";
                    case 12170, 12535, 18714:
                        return "Se agotó el tiempo de espera. Revise la disponibilidad de Oracle.";
                    default:
                        break;
                }
            }
        }
        return "No fue posible establecer la conexión con Oracle. Revise host, puerto, servicio y disponibilidad. La configuración activa no se modificó.";
    }
}
