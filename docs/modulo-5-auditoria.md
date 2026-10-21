# Módulo 5: Auditoría

Pantalla de consulta (`GET /auditoria`) que obtiene directamente del diccionario de datos de Oracle los
usuarios, roles, privilegios y objetos con problemas del contenedor al que está conectada la aplicación.
No modifica la base de datos: la recompilación de objetos y la gestión de usuarios o privilegios
corresponden a otros módulos.

## Acceso

1. Conecte una instancia en **Cambiar conexión** (`/conexion`).
2. Abra **Auditoría** en el menú principal.
3. Elija una pestaña: **Usuarios**, **Roles**, **Privilegios** u **Objetos con problemas**.

Cada pestaña ejecuta solo sus propias consultas. Los filtros comunes son:

| Filtro | Parámetro | Efecto |
|---|---|---|
| Origen | `origen=TODOS\|ORGANIZACION\|ORACLE` | Usa la columna `ORACLE_MAINTAINED` para separar lo creado por la organización de lo que crea y mantiene Oracle. |
| Buscar | `buscar` | Búsqueda sin distinguir mayúsculas. Los caracteres `%` y `_` se tratan como texto literal. |
| Filas por tabla | `limite` (10 a 500, por defecto 200) | Limita cada tabla e indica cuántas filas existen en total. |
| Usuario o rol a auditar | `beneficiario` | Solo en Privilegios. Por defecto es la cuenta de la sesión. |
| Origen del privilegio | `alcance=TODOS\|DIRECTOS\|ROLES` | Solo en Privilegios. |

## Arquitectura

```
auditoria.html  ──GET /auditoria──▶  AuditoriaController
                                         │ convierte parámetros en enums (VistaAuditoria, FiltroOrigen, OrigenPrivilegio)
                                         ▼
                                     AuditoriaService
                                         │ normaliza filtros (límite, texto) e interpreta privilegios (observaciones)
                                         ▼
                                     AuditoriaRepository ──JdbcTemplate──▶ Oracle (vistas DBA_* / V$)
                                         │ usa ConexionOracleService (conexión activa) y ConsultasOracle (errores ORA)
                                         ▼
                                     AuditoriaDto (records inmutables)
```

Cada consulta se ejecuta por separado mediante `ConsultasOracle.leerTolerandoVacio`:

- **Lista vacía**: Oracle respondió sin filas. La pantalla muestra, por ejemplo, «Sin problemas».
- **`null` más un aviso**: la consulta falló (ORA-00942, ORA-01031, tiempo agotado…). La pantalla muestra
  «No disponible» y el aviso. Así la falta de permisos nunca se presenta como ausencia de problemas.

## Consultas administrativas

Todas son de solo lectura. Los valores ingresados por el usuario viajan siempre como parámetros
enlazados (`?`). Solo se concatenan fragmentos SQL fijos definidos en el código.

| Pestaña | Vistas del sistema | Propósito |
|---|---|---|
| Todas | `SYS_CONTEXT('USERENV', …)` | Contenedor consultado (`CON_NAME`, `CON_ID`) y cuenta de la sesión. |
| Todas (resumen) | `DBA_USERS`, `DBA_ROLES`, `DBA_OBJECTS` | Totales de usuarios, cuentas bloqueadas o expiradas, roles y objetos `INVALID`. |
| Usuarios | `DBA_USERS`, `DBA_ROLE_PRIVS`, `DBA_SYS_PRIVS` | Cuenta, estado, fechas de bloqueo, expiración y último ingreso, tipo de autenticación, perfil, tablespace, si es común o local, si la mantiene Oracle y cuántos roles y privilegios de sistema tiene directamente. |
| Usuarios | `V$PWFILE_USERS` | Cuentas del archivo de contraseñas con privilegios administrativos de instancia (SYSDBA, SYSOPER, SYSBACKUP, SYSDG, SYSKM, SYSASM). |
| Roles | `DBA_ROLES`, `DBA_ROLE_PRIVS`, `DBA_USERS` | Roles disponibles, forma de activación y cuántos usuarios y roles los reciben. |
| Roles | `DBA_ROLE_PRIVS` + `DBA_USERS` / `DBA_ROLES` | Asignaciones directas: qué usuario o rol recibió cada rol, con `ADMIN OPTION` y `DEFAULT_ROLE`. |
| Privilegios | `DBA_USERS` ∪ `DBA_ROLES` | Lista de usuarios y roles que se pueden auditar, y validación del nombre solicitado. |
| Privilegios | `DBA_ROLE_PRIVS` con `CONNECT BY NOCYCLE` | Roles directos y heredados (anidados). `SYS_CONNECT_BY_PATH` muestra el camino de herencia y `ROW_NUMBER()` conserva el camino más corto. |
| Privilegios | `DBA_SYS_PRIVS` | Privilegios de sistema del beneficiario y de sus roles efectivos, con su origen y `ADMIN OPTION`. Cuenta los privilegios `ANY` distintos. |
| Privilegios | `DBA_TAB_PRIVS` | Privilegios sobre objetos (propietario, objeto, tipo), con su origen, quién los otorgó y `GRANT OPTION`. |
| Objetos | `DBA_OBJECTS`, `DBA_ERRORS` | Objetos con `STATUS = 'INVALID'` y cantidad de errores de compilación de cada uno. |
| Objetos | `DBA_ERRORS` | Errores y advertencias de la última compilación (línea, posición, mensaje). |
| Objetos | `DBA_DEPENDENCIES`, `DBA_OBJECTS` | Dependencias problemáticas: objetos que dependen de un objeto inválido. |
| Objetos | `DBA_INDEXES`, `DBA_IND_PARTITIONS`, `DBA_IND_SUBPARTITIONS` | Índices, particiones y subparticiones de índice en estado `UNUSABLE`. |

Ejemplo: roles efectivos de un beneficiario.

```sql
SELECT ROL, NIVEL, RUTA, ADMIN_OPTION, DEFAULT_ROLE FROM (
    SELECT GRANTED_ROLE AS ROL, LEVEL AS NIVEL, SYS_CONNECT_BY_PATH(GRANTED_ROLE, ' > ') AS RUTA,
           ADMIN_OPTION, DEFAULT_ROLE,
           ROW_NUMBER() OVER (PARTITION BY GRANTED_ROLE ORDER BY LEVEL) AS FILA
    FROM DBA_ROLE_PRIVS
    START WITH GRANTEE = :beneficiario
    CONNECT BY NOCYCLE PRIOR GRANTED_ROLE = GRANTEE)
WHERE FILA = 1
ORDER BY NIVEL, ROL;
```

Ejemplo: privilegios de sistema directos y heredados mediante roles.

```sql
SELECT P.PRIVILEGE, P.GRANTEE, P.ADMIN_OPTION, COUNT(*) OVER () AS TOTAL
FROM DBA_SYS_PRIVS P
WHERE (P.GRANTEE = :beneficiario
       OR P.GRANTEE IN (SELECT GRANTED_ROLE FROM DBA_ROLE_PRIVS
                        START WITH GRANTEE = :beneficiario
                        CONNECT BY NOCYCLE PRIOR GRANTED_ROLE = GRANTEE))
ORDER BY CASE WHEN P.GRANTEE = :beneficiario THEN 0 ELSE 1 END, P.PRIVILEGE
FETCH FIRST :limite ROWS ONLY;
```

`COUNT(*) OVER ()` se calcula antes de `FETCH FIRST`, así que una sola consulta devuelve las filas
limitadas y el total real.

### Rendimiento medido

Medido en Oracle 23.26 Free (CDB$ROOT, 33 usuarios, 109 roles, unos 32 000 objetos). Todas las consultas
tardan menos de 1 s. La más costosa es la de privilegios sobre objetos de SYSTEM (6705 filas antes del
límite, 0,9 s). La aplicación corta cada consulta a los 5 s (`ConexionOracleService`).

## Permisos mínimos requeridos

La aplicación **no otorga permisos**. La cuenta usada para conectarse necesita:

- `CREATE SESSION`
- `SELECT_CATALOG_ROLE`, que da lectura de las vistas `DBA_*` y `V$*` usadas, o bien
  `SELECT ANY DICTIONARY`.

Ejemplo para que un DBA cree una cuenta de solo consulta. **No lo ejecuta la aplicación** y la contraseña
debe definirse fuera del repositorio:

```sql
-- En CDB$ROOT un usuario común debe llevar el prefijo C##; en un PDB no.
CREATE USER C##MONITOR IDENTIFIED BY "<contraseña segura>";
GRANT CREATE SESSION, SELECT_CATALOG_ROLE TO C##MONITOR CONTAINER = ALL;
```

Si falta el permiso, la pantalla muestra «No disponible» en las secciones afectadas, el aviso con la vista
que falló (por ejemplo, `Usuarios (DBA_USERS): vista no accesible o inexistente.`) y una nota que indica
el rol requerido.

## Alcance y limitaciones

- **Contenedor consultado.** Las vistas `DBA_*` muestran el contenedor de la conexión. Conectado a
  `CDB$ROOT` se ven los usuarios comunes. Para auditar los usuarios locales de una base conectable
  (por ejemplo `FREEPDB1`), conéctese a ese servicio. La pantalla indica siempre el contenedor consultado.
- **Usuarios de base de datos y cuentas de servidor.** Oracle no tiene inicios de sesión de servidor
  separados de los usuarios de base de datos, como sí los tiene SQL Server. Como indicador equivalente
  se muestran:
  - las cuentas del archivo de contraseñas con privilegios administrativos de instancia
    (`V$PWFILE_USERS`), que pueden conectarse aunque la base esté cerrada;
  - el tipo de autenticación de cada usuario: `EXTERNAL` significa autenticación del sistema operativo,
    por ejemplo `OPS$ORACLE`.
- **Privilegios efectivos.** Se incluyen los privilegios directos y los heredados por roles, incluso
  anidados. **No** se incluyen:
  - los privilegios otorgados a `PUBLIC`;
  - los privilegios por columna (`DBA_COL_PRIVS`);
  - los privilegios de esquema de 23ai (`DBA_SCHEMA_PRIVS`);
  - los privilegios implícitos del propietario sobre sus propios objetos.

  Un rol no predeterminado o protegido con contraseña solo aplica si la sesión lo activa con `SET ROLE`.
  Por eso la lista representa los privilegios **disponibles**, no necesariamente los activos en una sesión.
- **Observaciones de seguridad.** Se calculan sobre el resultado consultado:
  - si tiene el rol `DBA` (directo o heredado);
  - cuántos privilegios `ANY` distintos tiene;
  - cuántos privilegios o roles directos puede otorgar a otros (`ADMIN OPTION`);
  - si tiene roles no predeterminados.

  Si se aplica una búsqueda, el conteo de privilegios `ANY` se limita a ese resultado.
- **Objetos inválidos.** Es un concepto propio de Oracle: `STATUS = 'INVALID'` en `DBA_OBJECTS`. Se
  complementa con:
  - `DBA_ERRORS`, que contiene errores y advertencias de compilación;
  - `DBA_DEPENDENCIES`, que muestra la propagación de la invalidez;
  - los índices `UNUSABLE`, que `DBA_OBJECTS` no marca como inválidos.

  Un objeto inválido puede volverse válido solo cuando Oracle lo recompila al usarlo.
- **Compatibilidad.** Las consultas usan columnas disponibles desde Oracle 12.2, como `ORACLE_MAINTAINED`,
  `COMMON`, `LAST_LOGIN`, `DBA_TAB_PRIVS.TYPE`, `V$PWFILE_USERS.ACCOUNT_STATUS` y `FETCH FIRST`. Se
  verificaron contra Oracle 23.26 Free.

## Escenario opcional de prueba

La base recién creada no tiene objetos inválidos, errores de compilación ni usuarios con pocos privilegios.
El siguiente guion crea esos casos en `FREEPDB1` para demostrar el módulo. **Modifica la base de datos**:
ejecútelo solo si el equipo lo decide, conectado a `FREEPDB1` con una cuenta DBA.

```sql
CREATE USER DEMO_AUDITORIA IDENTIFIED BY "<contraseña de prueba>" QUOTA 10M ON USERS;
GRANT CREATE SESSION, CREATE TABLE, CREATE VIEW, CREATE PROCEDURE TO DEMO_AUDITORIA;
CREATE ROLE DEMO_LECTURA;
GRANT SELECT ANY TABLE TO DEMO_LECTURA;
GRANT DEMO_LECTURA TO DEMO_AUDITORIA;

CREATE TABLE DEMO_AUDITORIA.CLIENTES (ID NUMBER PRIMARY KEY, NOMBRE VARCHAR2(50));
CREATE VIEW DEMO_AUDITORIA.V_CLIENTES AS SELECT ID, NOMBRE FROM DEMO_AUDITORIA.CLIENTES;
CREATE OR REPLACE PROCEDURE DEMO_AUDITORIA.P_LISTAR AS
    V_TOTAL NUMBER;
BEGIN
    SELECT COUNT(*) INTO V_TOTAL FROM DEMO_AUDITORIA.V_CLIENTES;
END;
/
-- Invalida la vista y, por dependencia, el procedimiento.
ALTER TABLE DEMO_AUDITORIA.CLIENTES DROP COLUMN NOMBRE;
-- Procedimiento con error de compilación (queda INVALID y registrado en DBA_ERRORS).
CREATE OR REPLACE PROCEDURE DEMO_AUDITORIA.P_CON_ERROR AS
BEGIN
    SELECT COLUMNA_INEXISTENTE INTO V_NADA FROM DUAL;
END;
/
```

Qué se observa en la aplicación conectada a `FREEPDB1`:

- **Usuarios**: `DEMO_AUDITORIA` aparece como cuenta local creada por la organización.
- **Roles**: `DEMO_LECTURA` aparece asignado a `DEMO_AUDITORIA`.
- **Privilegios**: los privilegios directos y `SELECT ANY TABLE` aparecen con origen «Mediante el rol
  DEMO_LECTURA».
- **Objetos con problemas**: `V_CLIENTES`, `P_LISTAR` y `P_CON_ERROR` aparecen como inválidos, con sus
  errores y la dependencia `P_LISTAR → V_CLIENTES`.

Para revisar el caso de **permisos insuficientes**, conéctese con `DEMO_AUDITORIA`. No tiene
`SELECT_CATALOG_ROLE`, así que las secciones aparecen como «No disponible» con el aviso
correspondiente.

Limpieza:

```sql
DROP USER DEMO_AUDITORIA CASCADE;
DROP ROLE DEMO_LECTURA;
```
