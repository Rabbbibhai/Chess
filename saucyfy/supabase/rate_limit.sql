-- Optional server-side rate limit: max 60 library writes per user per minute.
-- Run once in the Supabase SQL editor.
create table if not exists public.rate_limit_log (
  id bigint generated always as identity primary key,
  user_id uuid not null,
  bucket text not null,
  at timestamptz not null default now()
);
create index if not exists rate_limit_log_lookup on public.rate_limit_log (user_id, bucket, at desc);
-- RLS on with no policies: the table is unreachable from the public API.
alter table public.rate_limit_log enable row level security;

create or replace function public.limit_library_writes()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  recent int;
begin
  select count(*) into recent
  from public.rate_limit_log
  where user_id = new.user_id and bucket = 'library_write' and at > now() - interval '1 minute';

  if recent >= 60 then
    raise exception 'Too many changes. Please wait a minute and try again.';
  end if;

  insert into public.rate_limit_log (user_id, bucket) values (new.user_id, 'library_write');
  delete from public.rate_limit_log where user_id = new.user_id and at < now() - interval '1 hour';
  return new;
end;
$$;

revoke all on function public.limit_library_writes() from public, anon, authenticated;

drop trigger if exists library_items_rate_limit on public.library_items;
create trigger library_items_rate_limit
  before insert or update on public.library_items
  for each row execute function public.limit_library_writes();
