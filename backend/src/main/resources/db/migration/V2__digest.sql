-- Drop changes of each settings group, kept for two days for the evening summary ("Günün özeti").
CREATE TABLE drop_moments (
    group_key  TEXT        NOT NULL,
    at         TIMESTAMPTZ NOT NULL,
    from_state TEXT        NOT NULL,
    to_state   TEXT        NOT NULL,
    upper_ring TEXT        NOT NULL,
    lower_ring TEXT        NOT NULL
);
CREATE INDEX drop_moments_group_at ON drop_moments (group_key, at);

-- Devices that already got the summary of a day (in their own time zone), so a restart sends none twice.
CREATE TABLE digests_sent (
    device_id TEXT        NOT NULL,
    day       DATE        NOT NULL,
    sent_at   TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (device_id, day)
);
