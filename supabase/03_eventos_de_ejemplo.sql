-- ============================================================================
-- Kode App · 03_eventos_de_ejemplo.sql  (OPCIONAL)
-- Los 3 eventos de ejemplo que la versión SQLite insertaba al crear la base.
-- Sin creador (creator_id = null): nadie puede editarlos ni inscribirse como creador.
-- Ejecútalo una sola vez, en el SQL Editor (allí auth.uid() es null, por eso queda sin creador).
-- Si events.creator_id no admite null, no ejecutes este script.
-- ============================================================================
insert into public.events (creator_id, title, description, event_date, location, category, capacity)
select null, v.title, v.description, v.event_date::timestamptz, v.location, v.category, v.capacity
from (values
  ('Conferencia de Tecnología & IA 2026', 'Innovación, inteligencia artificial y desarrollo de software.', '2026-09-20 18:00-05', 'Centro de Convenciones, Lima', 'Tecnología', 400),
  ('Festival de Diseño y Arquitectura',   'Diseño, creatividad y nuevas tendencias.',                        '2026-09-26 16:00-05', 'Miraflores, Lima',              'Diseño',      200),
  ('Summit Fundadores & Startups',        'Comunidad, emprendimiento e innovación.',                         '2026-10-05 19:30-05', 'San Isidro, Lima',              'Comunidad',   150)
) as v(title, description, event_date, location, category, capacity)
where not exists (select 1 from public.events e where e.title = v.title);
