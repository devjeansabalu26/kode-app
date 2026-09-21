-- ============================================================================
-- Kode App · 01_diagnostico.sql  (SOLO LECTURA, no modifica nada)
-- Ejecútalo en Supabase → SQL Editor ANTES del script 02 y compara el resultado.
-- ============================================================================

-- 1) Columnas actuales de las tablas de la app
select table_name, column_name, data_type, udt_name, is_nullable, column_default
from information_schema.columns
where table_schema = 'public' and table_name in ('profiles', 'events', 'registrations')
order by table_name, ordinal_position;

-- 2) Restricciones (PK, FK, UNIQUE, CHECK)
select conrelid::regclass as tabla, conname, contype, pg_get_constraintdef(oid) as definicion
from pg_constraint
where conrelid in ('public.profiles'::regclass, 'public.events'::regclass, 'public.registrations'::regclass)
order by 1, 3;

-- 3) RLS activado y políticas existentes
select c.relname as tabla, c.relrowsecurity as rls_activo
from pg_class c join pg_namespace n on n.oid = c.relnamespace
where n.nspname = 'public' and c.relname in ('profiles', 'events', 'registrations');

select tablename, policyname, cmd, roles, qual, with_check
from pg_policies
where schemaname = 'public' and tablename in ('profiles', 'events', 'registrations')
order by tablename, policyname;

-- 4) Triggers sobre auth.users y sobre las tablas de la app
select event_object_schema, event_object_table, trigger_name, action_timing, event_manipulation
from information_schema.triggers
where (event_object_schema = 'auth' and event_object_table = 'users')
   or (event_object_schema = 'public' and event_object_table in ('profiles', 'events', 'registrations'));

-- 5) Vistas y funciones del esquema public
select table_name as vista from information_schema.views where table_schema = 'public';
select routine_name, routine_type from information_schema.routines where routine_schema = 'public';

-- 6) Cantidad de filas
select 'profiles' as tabla, count(*) from public.profiles
union all select 'events', count(*) from public.events
union all select 'registrations', count(*) from public.registrations;
