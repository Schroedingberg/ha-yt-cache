CREATE TABLE IF NOT EXISTS jobs (
  id          TEXT PRIMARY KEY,
  url         TEXT NOT NULL,
  video_id    TEXT,
  state       TEXT NOT NULL
              CHECK (state IN ('queued', 'running', 'completed', 'failed')),
  created_at  INTEGER NOT NULL,
  updated_at  INTEGER NOT NULL,
  started_at  INTEGER,
  finished_at INTEGER,
  attempts    INTEGER NOT NULL DEFAULT 0,
  progress    REAL NOT NULL DEFAULT 0,
  message     TEXT,
  error       TEXT
);

CREATE INDEX IF NOT EXISTS jobs_queue
  ON jobs (state, created_at, id);
