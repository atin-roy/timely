-- One row per way a user can sign in: a password, Google, GitHub, and so on.
-- Provider is plain text so a new one needs no migration.
-- provider_user_id is Google's `sub`, GitHub's numeric id, or the user's own id for a password. Never the email.
CREATE TABLE auth_identities (
  provider text NOT NULL,
  provider_user_id text NOT NULL,
  user_id integer NOT NULL REFERENCES users ON DELETE CASCADE,
  password_hash text,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),

  PRIMARY KEY (provider, provider_user_id),
  -- A user has at most one password and one identity per provider.
  UNIQUE (user_id, provider),
  CHECK ((provider = 'password') = (password_hash IS NOT NULL))
);

CREATE TRIGGER auth_identities_set_updated_at BEFORE UPDATE ON auth_identities
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Login tokens. Only the SHA-256 of the token is stored, so a leaked table holds nothing usable.
-- Not "sessions": that already means a focus or break session.
CREATE TABLE auth_tokens (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  user_id integer NOT NULL REFERENCES users ON DELETE CASCADE,
  token_hash bytea NOT NULL UNIQUE CHECK (length(token_hash) = 32),
  device_name text,
  created_at timestamptz NOT NULL DEFAULT now(),
  expires_at timestamptz NOT NULL
);

-- "Log out everywhere" deletes all of a user's tokens.
CREATE INDEX ON auth_tokens (user_id);
