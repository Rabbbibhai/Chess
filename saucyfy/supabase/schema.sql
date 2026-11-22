-- Saucyfy schema. Run once in the Supabase SQL editor.
create table if not exists public.library_items (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  media_type text not null check (media_type in ('movie','tv','anime')),
  external_id text not null,
  title text not null,
  poster_url text,
  year text,
  overview text,
  status text not null default 'plan_to_watch'
    check (status in ('watching','watched','plan_to_watch','dropped')),
  rating smallint check (rating between 1 and 10),
  season int,
  episode int,
  notes text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (user_id, media_type, external_id)
);

create index if not exists library_items_user_updated_idx
  on public.library_items (user_id, updated_at desc);

alter table public.library_items enable row level security;

create policy "read own library" on public.library_items
  for select to authenticated using ((select auth.uid()) = user_id);
create policy "insert own library" on public.library_items
  for insert to authenticated with check ((select auth.uid()) = user_id);
create policy "update own library" on public.library_items
  for update to authenticated using ((select auth.uid()) = user_id)
  with check ((select auth.uid()) = user_id);
create policy "delete own library" on public.library_items
  for delete to authenticated using ((select auth.uid()) = user_id);

create or replace function public.touch_updated_at()
returns trigger language plpgsql set search_path = '' as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

create trigger library_items_touch
  before update on public.library_items
  for each row execute function public.touch_updated_at();
