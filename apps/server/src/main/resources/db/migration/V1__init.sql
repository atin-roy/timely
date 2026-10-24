CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TYPE session_kind AS ENUM ('focus', 'break');
CREATE TYPE session_source AS ENUM ('timed', 'logged');
CREATE TYPE session_mode AS ENUM ('stopwatch', 'countdown');

CREATE TABLE users (
  id integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  username text NOT NULL,
  email text NOT NULL,
  timezone text NOT NULL DEFAULT 'UTC',
  focus_minutes integer NOT NULL DEFAULT 25 CHECK (focus_minutes > 0),
  short_break_minutes integer NOT NULL DEFAULT 5 CHECK (short_break_minutes > 0),
  long_break_minutes integer NOT NULL DEFAULT 15 CHECK (long_break_minutes > 0),
  pomodoros_per_long_break integer NOT NULL DEFAULT 4 CHECK (pomodoros_per_long_break > 0),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX ON users (lower(email));

CREATE TABLE activities (
  id integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  user_id integer NOT NULL REFERENCES users ON DELETE CASCADE,
  name text NOT NULL,
  color text NOT NULL CHECK (color ~ '^#[0-9a-fA-F]{6}$'),
  archived_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (user_id, id)
);

-- Archived activities keep their name reserved; reusing it means unarchiving.
CREATE UNIQUE INDEX ON activities (user_id, lower(name));

-- Settings are copied from the user when the cycle starts, so later changes only affect future cycles.
-- A cycle whose last session is deleted is deleted with it.
CREATE TABLE pomodoro_cycles (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  user_id integer NOT NULL REFERENCES users ON DELETE CASCADE,
  focus_minutes integer NOT NULL CHECK (focus_minutes > 0),
  short_break_minutes integer NOT NULL CHECK (short_break_minutes > 0),
  long_break_minutes integer NOT NULL CHECK (long_break_minutes > 0),
  pomodoros_per_long_break integer NOT NULL CHECK (pomodoros_per_long_break > 0),
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (user_id, id)
);

CREATE TABLE sessions (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  user_id integer NOT NULL REFERENCES users ON DELETE CASCADE,
  kind session_kind NOT NULL,
  activity_id integer,
  source session_source NOT NULL,
  mode session_mode,
  planned_seconds integer CHECK (planned_seconds > 0),
  pomodoro_cycle_id bigint,
  -- The timezone the user was in, which fixes the session's days for good.
  timezone text NOT NULL,
  started_at timestamptz NOT NULL,
  ended_at timestamptz,
  completed boolean NOT NULL GENERATED ALWAYS AS (
    coalesce(mode = 'countdown' AND ended_at - started_at >= planned_seconds * interval '1 second', false)
  ) STORED,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),

  FOREIGN KEY (user_id, activity_id) REFERENCES activities (user_id, id) ON DELETE CASCADE,
  FOREIGN KEY (user_id, pomodoro_cycle_id) REFERENCES pomodoro_cycles (user_id, id),

  CHECK ((kind = 'focus') = (activity_id IS NOT NULL)),
  CHECK ((source = 'logged') = (mode IS NULL)),
  CHECK ((mode IS NOT DISTINCT FROM 'countdown') = (planned_seconds IS NOT NULL)),
  CHECK (pomodoro_cycle_id IS NULL OR mode IS NOT DISTINCT FROM 'countdown'),
  CHECK (ended_at > started_at),
  CHECK (source = 'timed' OR ended_at IS NOT NULL),

  -- A running session occupies everything from its start onward.
  EXCLUDE USING gist (
    user_id WITH =,
    tstzrange(started_at, coalesce(ended_at, 'infinity'), '[)') WITH &&
  )
);

CREATE UNIQUE INDEX ON sessions (user_id) WHERE ended_at IS NULL;
CREATE INDEX ON sessions (user_id, started_at);
CREATE INDEX ON sessions (activity_id);
CREATE INDEX ON sessions (pomodoro_cycle_id);

CREATE FUNCTION set_updated_at() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  NEW.updated_at = now();
  RETURN NEW;
END;
$$;

CREATE TRIGGER users_set_updated_at BEFORE UPDATE ON users
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER sessions_set_updated_at BEFORE UPDATE ON sessions
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- A trigger rather than app code, so cascaded deletes (like wiping an activity) are covered too.
CREATE FUNCTION delete_empty_pomodoro_cycle() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  DELETE FROM pomodoro_cycles c
  WHERE c.id = OLD.pomodoro_cycle_id
    AND NOT EXISTS (SELECT 1 FROM sessions s WHERE s.pomodoro_cycle_id = OLD.pomodoro_cycle_id);
  RETURN NULL;
END;
$$;

CREATE TRIGGER sessions_delete_empty_pomodoro_cycle AFTER DELETE ON sessions
  FOR EACH ROW WHEN (OLD.pomodoro_cycle_id IS NOT NULL)
  EXECUTE FUNCTION delete_empty_pomodoro_cycle();

-- Derived from sessions, so edits and deletes show up immediately.
-- Break rows have a null activity_id. Running sessions count up to now().
CREATE VIEW daily_stats AS
SELECT
  s.user_id,
  s.kind,
  s.activity_id,
  d.day,
  sum(extract(epoch FROM least(b.session_end, d.day_end) - greatest(s.started_at, d.day_start)))::bigint AS total_seconds,
  count(*) FILTER (WHERE d.day = b.start_day) AS total_sessions
FROM sessions s
CROSS JOIN LATERAL (
  SELECT
    (s.started_at AT TIME ZONE s.timezone)::date AS start_day,
    -- A countdown past its planned end is over even before the server writes ended_at.
    coalesce(
      s.ended_at,
      CASE WHEN s.mode = 'countdown'
           THEN least(now(), s.started_at + s.planned_seconds * interval '1 second')
           ELSE now() END
    ) AS session_end
) b
CROSS JOIN LATERAL (
  SELECT
    g::date AS day,
    g AT TIME ZONE s.timezone AS day_start,
    (g + interval '1 day') AT TIME ZONE s.timezone AS day_end
  FROM generate_series(
    b.start_day::timestamp,
    (b.session_end AT TIME ZONE s.timezone)::date::timestamp,
    interval '1 day'
  ) g
) d
WHERE least(b.session_end, d.day_end) > greatest(s.started_at, d.day_start)
GROUP BY s.user_id, s.kind, s.activity_id, d.day;
