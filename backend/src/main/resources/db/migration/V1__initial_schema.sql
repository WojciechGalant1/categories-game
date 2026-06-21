CREATE TABLE rooms (
    code             varchar(10)  NOT NULL,
    answers          jsonb,
    current_letter   varchar(255),
    current_round    integer,
    round_ends_at    bigint,
    round_started_at bigint,
    stopped_players  jsonb,
    votes            jsonb,
    is_public        boolean      NOT NULL,
    scores           jsonb,
    categories       jsonb,
    max_players      integer,
    rounds           integer,
    time_per_round   integer,
    status           varchar(255),
    CONSTRAINT rooms_pkey PRIMARY KEY (code),
    CONSTRAINT rooms_status_check CHECK (
        status IN ('lobby', 'playing', 'reviewing', 'finished')
    )
);

CREATE TABLE players (
    id        uuid         NOT NULL,
    is_host   boolean      NOT NULL,
    nick      varchar(255),
    room_code varchar(10),
    CONSTRAINT players_pkey PRIMARY KEY (id),
    CONSTRAINT fk_players_room_code
        FOREIGN KEY (room_code) REFERENCES rooms(code) ON DELETE CASCADE
);

CREATE INDEX idx_players_room_code ON players(room_code);
