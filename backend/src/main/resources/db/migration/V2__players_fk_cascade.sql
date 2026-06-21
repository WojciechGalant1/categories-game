-- Brownfield fix: idempotent upgrade from Hibernate ddl-auto schema.
-- Safe to run after V1 (drops/re-adds FK, ensures varchar(10) and index).

DO $$
DECLARE r RECORD;
BEGIN
  FOR r IN
    SELECT con.conname
    FROM pg_constraint con
    JOIN pg_class rel ON rel.oid = con.conrelid
    JOIN pg_attribute att ON att.attrelid = con.conrelid AND att.attnum = ANY(con.conkey)
    WHERE rel.relname = 'players' AND att.attname = 'room_code' AND con.contype = 'f'
  LOOP
    EXECUTE format('ALTER TABLE players DROP CONSTRAINT %I', r.conname);
  END LOOP;
END $$;

ALTER TABLE rooms ALTER COLUMN code TYPE varchar(10);
ALTER TABLE players ALTER COLUMN room_code TYPE varchar(10);

ALTER TABLE players
  ADD CONSTRAINT fk_players_room_code
  FOREIGN KEY (room_code) REFERENCES rooms(code) ON DELETE CASCADE;

CREATE INDEX IF NOT EXISTS idx_players_room_code ON players(room_code);
