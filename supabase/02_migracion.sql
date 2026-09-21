-- ============================================================================
-- Kode App · 02_migracion.sql
-- Crea/ajusta profiles, events, registrations + RLS + vista de conteos + funciones.
-- Es IDEMPOTENTE (se puede ejecutar varias veces). Ejecútalo en Supabase → SQL Editor.
--
-- OJO: la sección 1 BORRA todas las políticas RLS que ya existan en estas 3 tablas
-- y las reemplaza por las de este script (así no queda ninguna política antigua que
-- permita, por ejemplo, insertar inscripciones saltándose el control de aforo).
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 0) TABLAS (si ya existen, solo se agregan las columnas que falten)
-- ----------------------------------------------------------------------------

-- profiles: datos extra del usuario. La contraseña vive en Supabase Auth, NO aquí.
create table if not exists public.profiles (
  id         uuid primary key references auth.users (id) on delete cascade,
  name       text not null,
  dni        text not null,
  phone      text not null,
  gender     text not null,
  age        integer not null check (age between 1 and 120),
  email      text not null,          -- copia del correo de auth.users; la pone el trigger (NEW.email), no la app
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
alter table public.profiles add column if not exists name       text;
alter table public.profiles add column if not exists dni        text;
alter table public.profiles add column if not exists phone      text;
alter table public.profiles add column if not exists gender     text;
alter table public.profiles add column if not exists age        integer;
alter table public.profiles add column if not exists email      text;
alter table public.profiles add column if not exists created_at timestamptz default now();
alter table public.profiles add column if not exists updated_at timestamptz default now();
create unique index if not exists profiles_dni_key on public.profiles (dni);

-- email: se rellena desde auth.users en filas que ya existieran, y es UNIQUE y NOT NULL
update public.profiles p set email = u.email from auth.users u where u.id = p.id and p.email is null;
create unique index if not exists profiles_email_key on public.profiles (email);
do $$ begin
  if not exists (select 1 from public.profiles where email is null) then
    alter table public.profiles alter column email set not null;
  end if;
end $$;

-- events
create table if not exists public.events (
  id          bigint generated always as identity primary key,
  creator_id  uuid default auth.uid() references auth.users (id) on delete set null,
  title       text not null,
  description text,
  event_date  timestamptz not null,
  location    text not null,
  category    text not null,
  capacity    integer not null default 100 check (capacity > 0),
  status      text not null default 'PUBLISHED',
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now()
);
alter table public.events add column if not exists creator_id  uuid;
alter table public.events add column if not exists description text;
alter table public.events add column if not exists created_at  timestamptz default now();
alter table public.events add column if not exists updated_at  timestamptz default now();
-- el creador se toma SIEMPRE de la sesión (no de lo que mande la app)
alter table public.events alter column creator_id set default auth.uid();
-- capacidad positiva
do $$ begin
  if not exists (select 1 from pg_constraint where conname = 'events_capacity_positive') then
    alter table public.events add constraint events_capacity_positive check (capacity > 0);
  end if;
end $$;
-- valor por defecto de status solo si la columna es de tipo texto (si es enum se deja como está)
do $$ begin
  if exists (select 1 from information_schema.columns
             where table_schema = 'public' and table_name = 'events' and column_name = 'status'
               and data_type in ('text', 'character varying')) then
    alter table public.events alter column status set default 'PUBLISHED';
  end if;
end $$;
create index if not exists events_event_date_idx  on public.events (event_date);
create index if not exists events_category_idx    on public.events (category);
create index if not exists events_creator_id_idx  on public.events (creator_id);

-- registrations: inscripción de un usuario a un evento
create table if not exists public.registrations (
  id            bigint generated always as identity primary key,
  event_id      bigint not null references public.events (id) on delete cascade,
  user_id       uuid   not null references auth.users (id) on delete cascade,
  qr_code       text   not null unique,
  checked_in    boolean not null default false,
  registered_at timestamptz not null default now(),
  checked_in_at timestamptz,
  constraint registrations_event_id_user_id_key unique (event_id, user_id)
);
alter table public.registrations add column if not exists checked_in_at timestamptz;
alter table public.registrations add column if not exists registered_at timestamptz default now();
create index if not exists registrations_user_id_idx  on public.registrations (user_id);
create index if not exists registrations_event_id_idx on public.registrations (event_id);

-- ----------------------------------------------------------------------------
-- 1) RLS: activar y reemplazar políticas
-- ----------------------------------------------------------------------------
alter table public.profiles      enable row level security;
alter table public.events        enable row level security;
alter table public.registrations enable row level security;

do $$
declare p record;
begin
  for p in select schemaname, tablename, policyname from pg_policies
           where schemaname = 'public' and tablename in ('profiles', 'events', 'registrations')
  loop
    execute format('drop policy if exists %I on %I.%I', p.policyname, p.schemaname, p.tablename);
  end loop;
end $$;

-- Privilegios mínimos (RLS decide después qué filas se ven o se tocan)
revoke all on public.profiles, public.events, public.registrations from anon, authenticated;
grant select                   on public.profiles      to authenticated;
grant update (name, phone, gender, age) on public.profiles to authenticated;  -- id, dni y email NO se editan desde la app
grant select                   on public.events        to anon, authenticated;
grant insert, update, delete   on public.events        to authenticated;
grant select                   on public.registrations to authenticated;
-- registrations: NADIE inserta/edita/borra desde la app; solo las funciones de la sección 4.

-- profiles: cada usuario ve y edita SOLO su perfil (el alta la hace el trigger de la sección 3)
create policy profiles_select_own on public.profiles for select to authenticated using (id = auth.uid());
create policy profiles_update_own on public.profiles for update to authenticated using (id = auth.uid()) with check (id = auth.uid());

-- events: los publicados los puede ver cualquiera; crear/editar/borrar solo el creador
create policy events_select_published on public.events for select to anon, authenticated
  using (lower(status::text) = 'published' or creator_id = auth.uid());
create policy events_insert_own on public.events for insert to authenticated
  with check (creator_id = auth.uid());
create policy events_update_own on public.events for update to authenticated
  using (creator_id = auth.uid()) with check (creator_id = auth.uid());
create policy events_delete_own on public.events for delete to authenticated
  using (creator_id = auth.uid());

-- registrations: el usuario ve las suyas; el organizador ve las de sus eventos
create policy registrations_select_own_or_organizer on public.registrations for select to authenticated
  using (user_id = auth.uid()
         or exists (select 1 from public.events e where e.id = registrations.event_id and e.creator_id = auth.uid()));

-- ----------------------------------------------------------------------------
-- 2) VISTA con conteos (inscritos / asistentes)
--    Todos necesitan saber cuántos hay inscritos (para "Aforo completo") pero NO deben ver
--    las inscripciones de otros. La vista expone solo los NÚMEROS y solo eventos publicados.
--    (Supabase puede avisar "Security Definer View": es intencional.)
-- ----------------------------------------------------------------------------
drop view if exists public.events_with_counts;
create view public.events_with_counts as
select e.id, e.creator_id, e.title, e.description, e.event_date, e.location, e.category, e.capacity,
       e.created_at, e.updated_at,
       (select count(*) from public.registrations r where r.event_id = e.id)::integer                    as registered_count,
       (select count(*) from public.registrations r where r.event_id = e.id and r.checked_in)::integer   as attended_count
from public.events e
where lower(e.status::text) = 'published';
revoke all on public.events_with_counts from anon, authenticated;
grant select on public.events_with_counts to anon, authenticated;

-- ----------------------------------------------------------------------------
-- 3) TRIGGERS
-- ----------------------------------------------------------------------------

-- 3a) Al registrarse un usuario en Auth, crear su fila en profiles con los datos que envió la app.
create or replace function public.handle_new_user() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  if new.raw_user_meta_data ? 'dni' then
    insert into public.profiles (id, name, dni, phone, gender, age, email)
    values (new.id,
            new.raw_user_meta_data ->> 'name',
            new.raw_user_meta_data ->> 'dni',
            new.raw_user_meta_data ->> 'phone',
            new.raw_user_meta_data ->> 'gender',
            (new.raw_user_meta_data ->> 'age')::integer,
            new.email);              -- el correo sale de Auth (NEW.email), NUNCA de lo que envíe la app
  end if;
  return new;
end $$;
drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created after insert on auth.users
  for each row execute function public.handle_new_user();

-- 3a-bis) Si el usuario cambia su correo en Auth, profiles.email se mantiene igual.
create or replace function public.handle_user_email_change() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  update public.profiles set email = new.email, updated_at = now() where id = new.id;
  return new;
end $$;
drop trigger if exists on_auth_user_email_changed on auth.users;
create trigger on_auth_user_email_changed after update of email on auth.users
  for each row when (old.email is distinct from new.email)
  execute function public.handle_user_email_change();

-- 3b) events: el creador es SIEMPRE el usuario autenticado; updated_at automático;
--     no permitir bajar la capacidad por debajo de los inscritos.
create or replace function public.events_before_write() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  if tg_op = 'INSERT' then
    if auth.uid() is not null then new.creator_id := auth.uid(); end if;
  else
    new.creator_id := old.creator_id;   -- el creador no se puede cambiar
    new.updated_at := now();
    if new.capacity is distinct from old.capacity
       and new.capacity < (select count(*) from public.registrations where event_id = old.id) then
      raise exception 'CAPACITY_BELOW_REGISTERED';
    end if;
  end if;
  return new;
end $$;
drop trigger if exists events_before_write on public.events;
create trigger events_before_write before insert or update on public.events
  for each row execute function public.events_before_write();

-- ----------------------------------------------------------------------------
-- 4) FUNCIONES (RPC) llamadas desde la app
-- ----------------------------------------------------------------------------

-- 4a) ¿DNI libre? Pública (sin sesión) porque se usa durante el registro; devuelve solo true/false.
create or replace function public.is_dni_available(p_dni text) returns boolean
language sql stable security definer set search_path = public as $$
  select not exists (select 1 from public.profiles where dni = p_dni);
$$;
revoke all on function public.is_dni_available(text) from public;
grant execute on function public.is_dni_available(text) to anon, authenticated;

-- 4b) Inscribirse a un evento. UNA transacción: bloquea la fila del evento (for update), así dos
--     usuarios simultáneos no pueden superar el aforo. Devuelve el código QR.
create or replace function public.register_for_event(p_event_id bigint) returns text
language plpgsql security definer set search_path = public as $$
declare
  v_uid   uuid := auth.uid();
  v_event public.events%rowtype;
  v_qr    text;
  v_count integer;
begin
  if v_uid is null then raise exception 'NOT_AUTHENTICATED'; end if;

  select * into v_event from public.events
   where id = p_event_id and lower(status::text) = 'published'
   for update;
  if not found then raise exception 'EVENT_NOT_FOUND'; end if;

  if v_event.creator_id = v_uid then raise exception 'IS_CREATOR'; end if;

  select qr_code into v_qr from public.registrations where event_id = p_event_id and user_id = v_uid;
  if found then return v_qr; end if;                       -- ya inscrito: devuelve su QR

  select count(*) into v_count from public.registrations where event_id = p_event_id;
  if v_count >= v_event.capacity then raise exception 'EVENT_FULL'; end if;

  v_qr := 'KODE-E' || p_event_id || '-U' || upper(substr(replace(v_uid::text, '-', ''), 1, 8))
          || '-' || upper(substr(replace(gen_random_uuid()::text, '-', ''), 1, 8));
  insert into public.registrations (event_id, user_id, qr_code) values (p_event_id, v_uid, v_qr);
  return v_qr;
end $$;
revoke all on function public.register_for_event(bigint) from public, anon;
grant execute on function public.register_for_event(bigint) to authenticated;

-- 4c) Marcar asistencia (check-in) leyendo el QR. Solo el organizador del evento.
--     La app todavía no tiene pantalla para esto; queda listo para usarse.
create or replace function public.check_in_registration(p_qr_code text) returns boolean
language plpgsql security definer set search_path = public as $$
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  update public.registrations r
     set checked_in = true, checked_in_at = now()
    from public.events e
   where r.qr_code = p_qr_code and e.id = r.event_id and e.creator_id = auth.uid() and not r.checked_in;
  return found;
end $$;
revoke all on function public.check_in_registration(text) from public, anon;
grant execute on function public.check_in_registration(text) to authenticated;
