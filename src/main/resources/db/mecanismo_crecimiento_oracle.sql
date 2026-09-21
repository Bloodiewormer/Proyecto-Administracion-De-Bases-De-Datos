-- ==============================================================================
-- UNIVERSIDAD NACIONAL DE COSTA RICA
-- CURSO: ADMINISTRACIÓN DE BASES DE DATOS (EIF402)
-- MÓDULO 3: GESTIÓN DEL ALMACENAMIENTO - CRECIMIENTO DE LA BASE DE DATOS
-- ==============================================================================
-- Este script documenta e implementa el mecanismo administrativo en Oracle Database
-- para registrar, comparar y analizar el crecimiento histórico del almacenamiento.
-- ==============================================================================

-- 1. Tabla administrativa para registrar snapshots periódicos de almacenamiento
CREATE TABLE HISTORIAL_CRECIMIENTO_BD (
    ID NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    FECHA TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
    BYTES_DATAFILES NUMBER NOT NULL,
    BYTES_TEMPFILES NUMBER NOT NULL,
    BYTES_REDO NUMBER DEFAULT 0 NOT NULL,
    BYTES_USADOS NUMBER NOT NULL,
    BYTES_LIBRES NUMBER NOT NULL,
    CANTIDAD_TABLESPACES NUMBER,
    CANTIDAD_ARCHIVOS NUMBER
);

COMMENT ON TABLE HISTORIAL_CRECIMIENTO_BD IS 'Registro histórico periódico de ocupación de almacenamiento de la base de datos Oracle';

-- 2. Procedimiento almacenado para capturar la métrica actual
CREATE OR REPLACE PROCEDURE SP_REGISTRAR_CRECIMIENTO AS
BEGIN
    INSERT INTO HISTORIAL_CRECIMIENTO_BD (
        BYTES_DATAFILES,
        BYTES_TEMPFILES,
        BYTES_REDO,
        BYTES_USADOS,
        BYTES_LIBRES,
        CANTIDAD_TABLESPACES,
        CANTIDAD_ARCHIVOS
    )
    SELECT
        (SELECT NVL(SUM(BYTES), 0) FROM DBA_DATA_FILES) AS BYTES_DATAFILES,
        (SELECT NVL(SUM(BYTES), 0) FROM DBA_TEMP_FILES) AS BYTES_TEMPFILES,
        (SELECT NVL(SUM(BYTES * MEMBERS), NVL(SUM(BYTES), 0)) FROM V$LOG) AS BYTES_REDO,
        (SELECT NVL(SUM(BYTES), 0) FROM DBA_SEGMENTS) AS BYTES_USADOS,
        (SELECT NVL(SUM(BYTES), 0) FROM DBA_FREE_SPACE) AS BYTES_LIBRES,
        (SELECT COUNT(*) FROM DBA_TABLESPACES) AS CANTIDAD_TABLESPACES,
        (SELECT (SELECT COUNT(*) FROM DBA_DATA_FILES) + (SELECT COUNT(*) FROM DBA_TEMP_FILES) FROM DUAL) AS CANTIDAD_ARCHIVOS
    FROM DUAL;
    
    COMMIT;
END;
/

-- 3. Vista analítica para consultar el crecimiento y variación relativa entre tomas
CREATE OR REPLACE VIEW V_CRECIMIENTO_ANALITICO AS
SELECT
    FECHA,
    ROUND((BYTES_DATAFILES + BYTES_TEMPFILES + BYTES_REDO) / (1024*1024), 2) AS TOTAL_MB,
    ROUND(BYTES_USADOS / (1024*1024), 2) AS USADO_MB,
    ROUND(BYTES_LIBRES / (1024*1024), 2) AS LIBRE_MB,
    ROUND(NVL(BYTES_USADOS - LAG(BYTES_USADOS, 1) OVER (ORDER BY FECHA), 0) / (1024*1024), 2) AS DELTA_MB,
    ROUND(
        CASE 
            WHEN LAG(BYTES_USADOS, 1) OVER (ORDER BY FECHA) > 0 THEN
                ((BYTES_USADOS - LAG(BYTES_USADOS, 1) OVER (ORDER BY FECHA)) * 100) /
                LAG(BYTES_USADOS, 1) OVER (ORDER BY FECHA)
            ELSE 0 
        END, 2
    ) AS PORCENTAJE_CRECIMIENTO
FROM HISTORIAL_CRECIMIENTO_BD;

-- 4. Programación del Job automático mediante DBMS_SCHEDULER (Ejecución diaria)
BEGIN
    DBMS_SCHEDULER.CREATE_JOB (
        job_name        => 'JOB_HISTORIAL_ALMACENAMIENTO',
        job_type        => 'PLSQL_BLOCK',
        job_action      => 'BEGIN SP_REGISTRAR_CRECIMIENTO; END;',
        start_date      => SYSTIMESTAMP,
        repeat_interval => 'FREQ=DAILY; BYHOUR=0; BYMINUTE=0; BYSECOND=0',
        enabled         => TRUE,
        comments        => 'Captura periódica automática de métricas de almacenamiento de Oracle'
    );
END;
/
