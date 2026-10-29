# Módulo 6: Mantenimiento preventivo

Pantalla `/mantenimiento` que **detecta** tareas de mantenimiento pendientes y permite **ejecutarlas** sobre
la instancia conectada:

- **Recalcular estadísticas** de una tabla o de un esquema completo (`DBMS_STATS`).
- **Detectar y recompilar objetos inválidos**, uno por uno (`ALTER … COMPILE`) o todo un esquema
  (`DBMS_UTILITY.COMPILE_SCHEMA`).

Después de cada operación se muestra una **confirmación** verde, amarilla o roja. Se arma consultando de
nuevo el diccionario de datos, así que informa el estado real que quedó en Oracle y no solo que el comando
terminó.

## Acceso

1. Conecte una instancia en **Cambiar conexión**. Para ver objetos de aplicación, use la base conectable;
   por ejemplo `FREEPDB1`.
2. Abra **Mantenimiento** en el menú principal.
3. Elija la pestaña **Estadísticas** o **Recompilación de objetos**.

## Alcance de seguridad

Las acciones se permiten solo sobre **esquemas creados por la organización** (`ORACLE_MAINTAINED = 'N'`).
`SYS`, `SYSTEM` y las demás cuentas internas no se tocan, porque Oracle las mantiene con procedimientos
propios que ejecuta un DBA: `utlrp.sql` para recompilar y `DBMS_STATS.GATHER_DICTIONARY_STATS` para las
estadísticas del diccionario.

Antes de ejecutar cualquier acción, el servicio verifica en el diccionario que el objetivo existe y pertenece a
la organización. Si no es así, la operación no se ejecuta y se informa el motivo.

## Arquitectura

```
mantenimiento.html ──GET /mantenimiento──────────────▶ MantenimientoController ──▶ MantenimientoService
                   ──POST /mantenimiento/estadisticas/tabla      (PRG: redirige        │ valida el objetivo,
                   ──POST /mantenimiento/estadisticas/esquema     al GET con el         │ ejecuta y verifica
                   ──POST /mantenimiento/recompilar/objeto        resultado como        ▼
                   ──POST /mantenimiento/recompilar/esquema       atributo flash)   MantenimientoRepository
                                                                                        │ JdbcTemplate +
                                                                                        ▼ CallableStatement
                                                                                     Oracle
```

- **Post/Redirect/Get.** Cada acción es un `POST` que redirige al `GET`. Si se recarga la página, la
  operación no se repite.
- **Confirmación.** `ResultadoMantenimiento` tiene nivel (EXITO, ADVERTENCIA o ERROR), operación, objetivo,
  detalle, duración y hora. Viaja como atributo flash.
- **Tiempo de espera.** Las consultas de monitoreo se cortan a los 5 s. Las operaciones de mantenimiento usan
  hasta **120 s** (`MantenimientoRepository.TIEMPO_MAXIMO_SEGUNDOS`): se amplía el tiempo de red solo en la
  conexión que ejecuta la operación, y el pool lo restablece al devolverla.

## Consultas de detección

| Vistas | Propósito |
|---|---|
| `DBA_TAB_STATISTICS` + `DBA_TABLES` + `DBA_USERS` | Tablas de la organización sin estadísticas (`LAST_ANALYZED IS NULL`) o desactualizadas (`STALE_STATS = 'YES'`), con filas registradas, último análisis y si las estadísticas están bloqueadas (`STATTYPE_LOCKED`). Se excluyen las tablas en la papelera (`DROPPED = 'NO'`). |
| `DBA_USERS` | Esquemas de la organización, para recalcular estadísticas de todo un esquema. |
| `DBA_OBJECTS` + `DBA_ERRORS` | Objetos con `STATUS = 'INVALID'`, cantidad de errores de cada uno y si su tipo admite recompilación individual. |
| `DBA_OBJECTS` (agrupado) | Esquemas con objetos inválidos y cuántos tiene cada uno. |
| `DBA_ERRORS` | Mensajes de la última compilación: línea, posición y texto. |

## Operaciones ejecutadas

Los nombres siempre viajan como **parámetros enlazados**. Además, `DBMS_ASSERT.ENQUOTE_NAME` los valida como
identificadores de Oracle y los entrecomilla. Ningún valor recibido del navegador se concatena en el SQL.

| Acción | Bloque PL/SQL | Verificación posterior |
|---|---|---|
| Estadísticas de una tabla | `DBMS_STATS.GATHER_TABLE_STATS(ownname => DBMS_ASSERT.ENQUOTE_NAME(?, FALSE), tabname => DBMS_ASSERT.ENQUOTE_NAME(?, FALSE), cascade => TRUE)` | Lee `NUM_ROWS` y `LAST_ANALYZED` en `DBA_TAB_STATISTICS`. |
| Estadísticas de un esquema | `DBMS_STATS.GATHER_SCHEMA_STATS(ownname => DBMS_ASSERT.ENQUOTE_NAME(?, FALSE), cascade => TRUE)` | Cuenta las tablas con `LAST_ANALYZED` posterior al inicio de la operación. Si alguna no se actualizó (por ejemplo, porque tiene estadísticas bloqueadas), lo informa como advertencia. |
| Recompilar un objeto | `EXECUTE IMMEDIATE 'ALTER <TIPO> ' \|\| DBMS_ASSERT.ENQUOTE_NAME(?, FALSE) \|\| '.' \|\| DBMS_ASSERT.ENQUOTE_NAME(?, FALSE) \|\| ' COMPILE [BODY]'` | Vuelve a leer el estado en `DBA_OBJECTS` y el primer error en `DBA_ERRORS`. |
| Recompilar un esquema | `DBMS_UTILITY.COMPILE_SCHEMA(schema => ?, compile_all => FALSE)` | Compara la cantidad de objetos inválidos antes y después. |

`<TIPO>` y `COMPILE [BODY]` vienen de una lista cerrada (`TipoCompilacion`): VIEW, MATERIALIZED VIEW,
PROCEDURE, FUNCTION, PACKAGE, PACKAGE BODY, TRIGGER, TYPE, TYPE BODY y SYNONYM. Otros tipos, como JAVA CLASS,
se recompilan con la opción de esquema completo.

### Interpretación del resultado

| Situación | Nivel |
|---|---|
| Estadísticas actualizadas en todas las tablas pedidas | Éxito |
| Estadísticas del esquema actualizadas solo en parte | Advertencia |
| El objeto recompilado quedó `VALID` | Éxito |
| Oracle compiló el objeto, pero sigue `INVALID` (ORA-24344) | Error, con el primer mensaje de compilación |
| Recompilación del esquema: todos válidos / algunos válidos / ninguno | Éxito / Advertencia / Error |
| Falta de privilegios, tabla inexistente (ORA-20000), estadísticas bloqueadas (ORA-20005), objeto en uso (ORA-04021), tiempo agotado | Error, con mensaje explicativo |

## Permisos requeridos

La aplicación **no otorga permisos**. La cuenta conectada necesita:

- **Para detectar:** `SELECT_CATALOG_ROLE`.
- **Para recalcular estadísticas** de otros esquemas: `ANALYZE ANY`.
- **Para recompilar** objetos de otros esquemas:
  - `ALTER ANY PROCEDURE` (procedimientos, funciones y paquetes);
  - `ALTER ANY TRIGGER`;
  - `ALTER ANY TYPE`;
  - `ALTER ANY MATERIALIZED VIEW`;
  - `ALTER ANY TABLE` (vistas);
  - `CREATE ANY SYNONYM` y `DROP ANY SYNONYM` (sinónimos).

  `DBMS_UTILITY.COMPILE_SCHEMA` se ejecuta con los privilegios de quien la invoca.

El rol `DBA`, que tiene `SYSTEM`, incluye todo lo anterior. Si falta un permiso, la operación termina en ERROR
con el mensaje «La cuenta conectada no tiene privilegios para ejecutar esta operación (ORA-01031)» o
ORA-20000, según el caso.

## Limitaciones

- Las acciones no abarcan objetos mantenidos por Oracle (ver *Alcance de seguridad*).
- `STALE_STATS` depende de la información de monitoreo de cambios (DML), que Oracle actualiza
  periódicamente. Una tabla recién modificada puede tardar unos minutos en aparecer como desactualizada.
- Recompilar no corrige errores de código. Si el objeto tiene un error real, por ejemplo una columna
  inexistente, sigue inválido y la pantalla muestra el error que hay que corregir.
- Las fechas de `LAST_ANALYZED` y `LAST_DDL_TIME` siguen el reloj del servidor de base de datos. En el
  contenedor Docker ese reloj está en UTC, así que puede diferir de la hora local de la aplicación.

## Escenario de demostración

Con los objetos del guion del Módulo 5 en `FREEPDB1` (`DEMO_AUDITORIA`):

1. **Recompilación con errores.** En *Recompilación*, presione **Recompilar** sobre `P_CON_ERROR`. Resultado:
   rojo, «sigue en estado INVALID», con el error ORA-00904.
2. **Recompilación que corrige una dependencia.** Este paso **modifica la base**. Restituya la columna
   eliminada desde SQL*Plus, conectado a `FREEPDB1`:
   ```sql
   ALTER TABLE DEMO_AUDITORIA.CLIENTES ADD (NOMBRE VARCHAR2(50));
   ```
   Luego presione **Recompilar esquema** con `DEMO_AUDITORIA`. Resultado: amarillo. `V_CLIENTES` y
   `P_LISTAR` vuelven a VALID y `P_CON_ERROR` sigue inválido porque su código tiene un error real.
3. **Tabla sin estadísticas.** Este paso **modifica la base**:
   ```sql
   CREATE TABLE DEMO_AUDITORIA.PEDIDOS (ID NUMBER PRIMARY KEY, TOTAL NUMBER);
   INSERT INTO DEMO_AUDITORIA.PEDIDOS SELECT LEVEL, LEVEL * 10 FROM DUAL CONNECT BY LEVEL <= 1000;
   COMMIT;
   ```
   En *Estadísticas* aparece `PEDIDOS` como «Sin estadísticas». Al presionar **Recalcular**, la confirmación
   verde informa 1000 filas registradas y la tabla desaparece de la lista de pendientes.

Limpieza (también elimina los objetos del Módulo 5):

```sql
DROP USER DEMO_AUDITORIA CASCADE;
DROP ROLE DEMO_LECTURA;
```
