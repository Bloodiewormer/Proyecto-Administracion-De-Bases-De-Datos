package cr.ac.una.dbmonitor.service;

import cr.ac.una.dbmonitor.dto.ConexionOracleDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Service;

import java.net.SocketTimeoutException;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.util.Properties;

@Service
public class ConexionOracleService {
    private volatile DriverManagerDataSource activeDataSource;

    public synchronized void conectar(ConexionOracleDto form) {
        DriverManagerDataSource candidate = crearDataSource(form);
        try (var connection = candidate.getConnection();
             var statement = connection.createStatement()) {
            statement.setQueryTimeout(5);
            try (var result = statement.executeQuery("SELECT 1 FROM DUAL")) {
                if (!result.next() || result.getInt(1) != 1) {
                    throw new IllegalStateException("Oracle no devolvió el resultado esperado. La configuración activa no se modificó.");
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException(mensajeError(ex));
        }
        activeDataSource = candidate;
    }

    DriverManagerDataSource crearDataSource(ConexionOracleDto form) {
        var source = new DriverManagerDataSource();
        source.setUrl("jdbc:oracle:thin:@//" + form.getHost().trim() + ":" + form.getPort()
                + "/" + form.getServiceName().trim());
        source.setUsername(form.getUsername().trim());
        source.setPassword(form.getPassword());
        var properties = new Properties();
        properties.setProperty("oracle.net.CONNECT_TIMEOUT", "5000");
        properties.setProperty("oracle.net.OUTBOUND_CONNECT_TIMEOUT", "5000");
        properties.setProperty("oracle.jdbc.ReadTimeout", "10000");
        source.setConnectionProperties(properties);
        return source;
    }

    public JdbcTemplate obtenerJdbcTemplate() {
        var source = activeDataSource;
        if (source == null) {
            throw new IllegalStateException("Primero configure una conexión Oracle.");
        }
        var template = new JdbcTemplate(source);
        template.setQueryTimeout(5);
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
