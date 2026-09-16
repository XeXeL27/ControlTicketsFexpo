-- PostgreSQL: ejecutar una vez sobre una base existente antes de emitir DOC.
-- Hibernate ddl-auto=update crea docente y ticket.id_docente al reiniciar,
-- pero una restricción CHECK anterior puede seguir excluyendo DOCENTE.
BEGIN;
ALTER TABLE ticket DROP CONSTRAINT IF EXISTS ticket_categoria_check;
ALTER TABLE ticket ADD CONSTRAINT ticket_categoria_check
    CHECK (categoria IN ('ESTUDIANTE', 'ADMINISTRATIVO', 'DOCENTE', 'EXTERNO'));
COMMIT;
