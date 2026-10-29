# Módulo 3: almacenamiento

## Análisis y decisión

El historial JSON y el script Oracle eran independientes. Las pruebas escribían en el archivo de producción; sus filas no identificaban la base. La captura toleraba falta de datos y errores de disco sin informar un fallo. El resumen mezclaba segmentos, archivos y redo; AWR refería una columna inexistente.

Se prioriza el mecanismo Oracle existente: tabla `HISTORIAL_CRECIMIENTO_BD`, procedimiento `SP_REGISTRAR_CRECIMIENTO` y vista `V_CRECIMIENTO_ANALITICO`. Será la única fuente de crecimiento. El JSON anterior se conserva, pero no se lee, migra ni escribe: no puede certificarse su procedencia. Mantener JSON como alternativa automática volvería a crear dos historiales; AWR permanece solo como referencia por tablespace, sin fusionarse con las mediciones propias.

## Plan y límites

1. Pruebas de regresión: captura sin datos, error de persistencia, éxito no falsificable por GET, métricas con el mismo alcance, aislamiento de bases y ausencia de escrituras JSON.
2. Integrar historial Oracle e identidad (DBID, DBID de contenedor, CON_ID, instancia, servicio y nombre del contenedor). Captura y confirmación en una conexión JDBC y transacción.
3. Corregir AWR con SNAP_ID/DBID, snapshots agrupados y filtros de contenedor.
4. Alinear script, cálculo y textos del módulo; conservar estructura HTML, clases y estilos.
5. Compilar, ejecutar pruebas de almacenamiento y suite, verificar hash del JSON y alcance del diff.

No se modifican otros módulos, conexión general, navegación, inicio ni CSS. La instalación y ejecución real del script requieren Oracle 21c XE en ejecución y los permisos administrativos descritos en este documento.

## Alcance de los indicadores

- **Tamaño actual:** suma del tamaño asignado de datafiles y tempfiles visibles en el contenedor actual. No representa el tamaño de toda la CDB desde un PDB ni incluye controlfiles, redo, backups o FRA.
- **Utilizado:** asignado menos extensiones libres para tablespaces permanentes/undo, más uso temporal de `V$TEMP_SPACE_HEADER`. Incluye espacio asignado a segmentos, no solo datos de filas.
- **Libre:** extensiones libres más espacio temporal libre; no es espacio libre del disco del servidor.
- **Porcentaje:** utilizado / asignado dentro del mismo alcance. Datos incompletos no se sustituyen por cero.
- **Redo:** cuando esté disponible, es un dato separado de instancia, nunca parte de ese porcentaje.
- **Crecimiento:** variación de espacio utilizado entre mediciones de la misma base/contenedor y alcance.

## Instalación reproducible: Oracle 21c XE, SYSTEM en XEPDB1

Después de clonar el repositorio, abrir una terminal en su directorio raíz.

Requisitos:

- Oracle 21c XE en ejecución.
- `XEPDB1` abierto en `READ WRITE`.
- SQL*Plus disponible.
- Contraseñas de `SYS` y `SYSTEM` definidas localmente.

Los ejemplos usan `localhost:1521`; sustituir host/puerto si Oracle está en otro equipo.

**No incluir contraseñas en comandos, archivos ni commits.** Utilizar `CONNECT` para introducirlas de forma interactiva cuando sea posible.

### 1. Prerrequisito administrativo (SYSDBA, una vez por instalación de XEPDB1)

Iniciar SQL*Plus desde la terminal:

```text
sqlplus /nolog
```

Conectarse como SYSDBA y cambiar explícitamente al contenedor de la aplicación:

```sql
CONNECT SYS@//localhost:1521/XE AS SYSDBA
ALTER SESSION SET CONTAINER = XEPDB1;
SHOW USER
SHOW CON_NAME
```

Debe aparecer `SYS` y `XEPDB1`.

En esa sesión ejecutar los ocho permisos directos:

```sql
GRANT SELECT ON SYS.DBA_DATA_FILES TO SYSTEM;
GRANT SELECT ON SYS.DBA_TEMP_FILES TO SYSTEM;
GRANT SELECT ON SYS.DBA_FREE_SPACE TO SYSTEM;
GRANT SELECT ON SYS.DBA_TABLESPACES TO SYSTEM;
GRANT SELECT ON SYS.V_$TEMP_SPACE_HEADER TO SYSTEM;
GRANT SELECT ON SYS.V_$DATABASE TO SYSTEM;
GRANT SELECT ON SYS.V_$INSTANCE TO SYSTEM;
GRANT SELECT ON SYS.V_$CONTAINERS TO SYSTEM;
```

Estos `GRANT` son un prerrequisito separado: no están dentro del script principal ejecutado como `SYSTEM`.

Los privilegios obtenidos únicamente mediante roles no sustituyen los `SELECT` directos necesarios para crear las vistas. Las vistas dinámicas se conceden sobre `SYS.V_$...`, aunque se consulten mediante `V$...`.

Si se recrea `XEPDB1` o se revocan los permisos, repetir este prerrequisito.

### 2. Instalar los objetos como SYSTEM en XEPDB1

En SQL*Plus cambiar de conexión; no ejecutar el script mientras el usuario siga siendo `SYS`:

```sql
CONNECT SYSTEM@//localhost:1521/XEPDB1
SHOW USER
SHOW CON_NAME
ALTER SESSION SET CURRENT_SCHEMA = SYSTEM;

SELECT SYS_CONTEXT('USERENV', 'CURRENT_SCHEMA') AS ESQUEMA
FROM DUAL;
```

Antes de ejecutar el archivo deben aparecer:

- Usuario: `SYSTEM`.
- Contenedor: `XEPDB1`.
- Esquema actual: `SYSTEM`.

Ejecutar el script desde la raíz del proyecto:

```sql
@src/main/resources/db/mecanismo_crecimiento_oracle.sql
```

La cuenta requiere `CREATE TABLE`, `CREATE VIEW`, `CREATE PROCEDURE`, `CREATE JOB` y capacidad de asignar espacio en su tablespace. `SYSTEM` en una instalación estándar de XE dispone de administración; si la instalación fue restringida, el DBA debe comprobar estos requisitos.

El orden del script es:

```text
tabla
→ migración
→ vista de almacenamiento
→ procedimiento
→ vista analítica
→ job
```

La migración es aditiva; no borra registros anteriores ni inventa la identidad de filas antiguas.

En una reinstalación con un job previamente habilitado, el DBA debe detenerlo o deshabilitarlo antes de ejecutar el script. La configuración final también lo deja deshabilitado.

### 3. Verificar la instalación

En la misma sesión `SYSTEM/XEPDB1`:

```sql
SELECT OBJECT_NAME, OBJECT_TYPE, STATUS
FROM USER_OBJECTS
WHERE OBJECT_NAME IN (
    'HISTORIAL_CRECIMIENTO_BD',
    'V_ALMACENAMIENTO_ACTUAL',
    'SP_REGISTRAR_CRECIMIENTO',
    'V_CRECIMIENTO_ANALITICO'
)
ORDER BY OBJECT_NAME;
```

Verificar errores de compilación:

```sql
SELECT NAME, TYPE, LINE, POSITION, TEXT
FROM USER_ERRORS
WHERE NAME IN (
    'V_ALMACENAMIENTO_ACTUAL',
    'SP_REGISTRAR_CRECIMIENTO',
    'V_CRECIMIENTO_ANALITICO'
)
ORDER BY NAME, SEQUENCE;
```

Comprobar la vista de almacenamiento:

```sql
SELECT *
FROM V_ALMACENAMIENTO_ACTUAL;
```

Comprobar la vista analítica:

```sql
SELECT ID, FECHA, TOTAL_MB, USADO_MB, LIBRE_MB
FROM V_CRECIMIENTO_ANALITICO
ORDER BY FECHA DESC, ID DESC
FETCH FIRST 5 ROWS ONLY;
```

Comprobar el job:

```sql
SELECT JOB_NAME, ENABLED, STATE, REPEAT_INTERVAL
FROM USER_SCHEDULER_JOBS
WHERE JOB_NAME = 'JOB_HISTORIAL_ALMACENAMIENTO';
```

Resultado esperado:

- Los cuatro objetos requeridos deben aparecer con `STATUS = VALID`.
- No deben existir errores de compilación.
- `V_ALMACENAMIENTO_ACTUAL` debe ser consultable.
- `V_CRECIMIENTO_ANALITICO` debe ser consultable.
- El job debe aparecer con `ENABLED = FALSE` y `STATE = DISABLED`.

El historial puede estar vacío en una instalación nueva.

Si `CREATE VIEW` falla, conservar el primer error `ORA-` o `SP2-` de la ejecución. La ausencia de filas en `USER_ERRORS` no demuestra que el objeto se haya creado correctamente.

**No habilitar el job automáticamente.**

### 4. Conexión de la aplicación

En la configuración de conexión de la aplicación utilizar el mismo host y puerto, servicio `XEPDB1` y usuario `SYSTEM`, con su contraseña local.

La aplicación no crea estos objetos automáticamente.

El botón **«Registrar medición actual»** llama al procedimiento `SP_REGISTRAR_CRECIMIENTO`. Java no duplica el `INSERT` ni utiliza el JSON heredado.

El procedimiento utiliza trece variables escalares explícitas:

- DBID.
- CON_DBID.
- CON_ID.
- INSTANCE_NAME.
- SERVICE_NAME.
- CON_NAME.
- BYTES_DATAFILES.
- BYTES_TEMPFILES.
- BYTES_TOTAL.
- BYTES_USADOS.
- BYTES_LIBRES.
- CANTIDAD_TABLESPACES.
- CANTIDAD_ARCHIVOS.

No utiliza `%ROWTYPE` ni un cursor auxiliar.

## Transacción y referencia AWR

El procedimiento no hace `COMMIT`. La aplicación confirma la fila devuelta y realiza el `COMMIT`; el job incluye su propio `COMMIT`.

Si fallan la lectura, validación o escritura, se realiza `ROLLBACK` y se informa el error. Una pérdida de conexión al confirmar puede dejar un resultado incierto; nunca se anuncia éxito sin confirmación.

AWR requiere acceso a `DBA_HIST_TBSPC_SPACE_USAGE` y `DBA_HIST_SNAPSHOT`, además de las condiciones de uso del ambiente.

Una falta de permisos o incompatibilidad se informa sin sustituir por datos ficticios.

`SNAP_ID` y `DBID` identifican el snapshot; `INSTANCE_NUMBER` existe en `DBA_HIST_SNAPSHOT`, no en la vista de consumo.

Se agrupa la fecha por snapshot para no multiplicar filas en RAC; el tablespace se relaciona por `TS#` y `CON_ID`.

El histórico se restringe al contenedor actual y su DBID.

## Validación realizada en Oracle

Se validó la instalación y ejecución del Módulo 3 sobre Oracle 21c XE utilizando el contenedor `XEPDB1` y el usuario `SYSTEM`.

Se verificaron los permisos directos requeridos, la creación y estado `VALID` de los objetos del módulo, la ejecución de `SP_REGISTRAR_CRECIMIENTO`, la persistencia de mediciones en `HISTORIAL_CRECIMIENTO_BD` y la consulta de `V_CRECIMIENTO_ANALITICO`.

También se comprobó la integración completa con la aplicación: el botón **«Registrar medición actual»** ejecutó correctamente el procedimiento Oracle y la interfaz mostró las mediciones almacenadas en el historial real de Oracle.

Durante la validación se registraron varias mediciones reales de almacenamiento en Oracle. Las mediciones incluyeron el tamaño de datafiles y tempfiles, espacio utilizado, espacio libre, identidad de la base de datos y del contenedor, cantidad de tablespaces y cantidad de archivos.

La validación real confirmó que las mediciones mostradas por la aplicación provienen directamente de Oracle y no del historial JSON heredado.

El job `JOB_HISTORIAL_ALMACENAMIENTO` se mantiene creado y deshabilitado (`ENABLED = FALSE`, `STATE = DISABLED), de acuerdo con la configuración del módulo.

Las pruebas de persistencia tras reinicio, múltiples PDB, datos offline, AWR real y ejecución automática del job no forman parte de la validación realizada en esta sesión y no se presentan como comprobadas.

## Verificación realizada (29/10/2026)

- Maven en modo offline con JDK 21: compilan código y pruebas; suite completa de 185 casos, cero fallos, cero errores y cero omitidos.
- Se verificó la instalación de los objetos Oracle del Módulo 3 y todos los objetos requeridos quedaron con `STATUS = VALID`.
- Se verificó la ejecución correcta de `SP_REGISTRAR_CRECIMIENTO` y la inserción de mediciones reales en `HISTORIAL_CRECIMIENTO_BD`.
- Se verificó la consulta de `V_CRECIMIENTO_ANALITICO` y la presentación del historial en la aplicación.
- Se comprobó el funcionamiento del botón **«Registrar medición actual»**, obteniendo una confirmación exitosa desde la interfaz.
- Se comprobó que las mediciones mostradas por la aplicación corresponden a información obtenida directamente de Oracle y no al historial JSON heredado.
- Se comprobaron mediciones reales con fecha y hora, mostrando el tamaño de datafiles y tempfiles, espacio utilizado y espacio libre.
- El job `JOB_HISTORIAL_ALMACENAMIENTO` permanece creado y deshabilitado.
- JSON heredado intacto antes y después de las pruebas: SHA-256 `96B8BC40F4C8CC78AF10DB4BDA229E376FB8608D30BD9E93ED2C9CA6A48176EB`.
- La plantilla conserva elementos, clases CSS y estilos inline en el mismo orden; cambian textos y enlaces de datos.
- No se alteran otros módulos, conexión general, navegación, inicio ni CSS.
- Se ejecutó el script del Módulo 3 sobre Oracle y se verificó su funcionamiento con información real.

## Referencias de columnas Oracle

- [DBA_HIST_TBSPC_SPACE_USAGE](https://docs.oracle.com/en/database/oracle/oracle-database/19/refrn/DBA_HIST_TBSPC_SPACE_USAGE.html)
- [DBA_HIST_SNAPSHOT](https://docs.oracle.com/en/database/oracle/oracle-database/19/refrn/DBA_HIST_SNAPSHOT.html)
- [V$TABLESPACE](https://docs.oracle.com/en/database/oracle/oracle-database/19/refrn/V-TABLESPACE.html)
- [V$TEMP_SPACE_HEADER](https://docs.oracle.com/en/database/oracle/oracle-database/19/refrn/V-TEMP_SPACE_HEADER.html)
- [V$CONTAINERS](https://docs.oracle.com/en/database/oracle/oracle-database/19/refrn/V-CONTAINERS.html)
