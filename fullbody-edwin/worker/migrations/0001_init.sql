CREATE TABLE IF NOT EXISTS logs (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  date TEXT NOT NULL,
  session TEXT NOT NULL,
  exercise TEXT NOT NULL,
  set_number INTEGER NOT NULL,
  weight REAL,
  reps INTEGER,
  rir REAL,
  created_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX IF NOT EXISTS idx_logs_exercise ON logs(exercise);
CREATE INDEX IF NOT EXISTS idx_logs_date ON logs(date);
CREATE UNIQUE INDEX IF NOT EXISTS uq_logs_date_exercise_set ON logs(date, exercise, set_number);
