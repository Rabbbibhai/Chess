create table users (
  discord_id text primary key,
  chess_username text not null,
  created_at timestamptz not null default now(),
  last_end_time bigint not null default 0  -- bookmark: only games after this are analyzed
);
create table analyzed_games (
  game_url text primary key,
  analyzed_at timestamptz not null default now()
);
-- RLS on with no policies: only the service key (server side) can read/write
alter table users enable row level security;
alter table analyzed_games enable row level security;
