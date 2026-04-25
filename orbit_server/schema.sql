CREATE TABLE IF NOT EXISTS usage_stats (
    id        SERIAL  PRIMARY KEY,
    device_id TEXT    NOT NULL UNIQUE,
    account   TEXT,
    nickname  TEXT,
    gmail     TEXT,
    edit      INTEGER NOT NULL DEFAULT 0,
    batch     INTEGER NOT NULL DEFAULT 0,
    game      INTEGER NOT NULL DEFAULT 0,
    online    INTEGER NOT NULL DEFAULT 0,
    replay    INTEGER NOT NULL DEFAULT 0
);
