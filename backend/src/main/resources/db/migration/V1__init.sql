-- One-minute OHLC candles per listed asset (docs/03).
CREATE TABLE candles (
    asset  TEXT             NOT NULL,
    start  TIMESTAMPTZ      NOT NULL,
    open   DOUBLE PRECISION NOT NULL,
    high   DOUBLE PRECISION NOT NULL,
    low    DOUBLE PRECISION NOT NULL,
    close  DOUBLE PRECISION NOT NULL,
    PRIMARY KEY (asset, start)
);

-- Engine memory per settings group (asset | intervals | thresholds), so restarts do not lose
-- pending changes or report spurious ones.
CREATE TABLE group_states (
    key        TEXT        PRIMARY KEY,
    memory     JSONB       NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

-- Anonymous devices: FCM token and watched assets with their settings.
CREATE TABLE devices (
    id         TEXT        PRIMARY KEY,
    fcm_token  TEXT,
    config     JSONB       NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
