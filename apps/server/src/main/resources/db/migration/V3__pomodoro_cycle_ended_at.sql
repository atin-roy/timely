-- Set when the user stops a cycle, or when it times out while waiting for the next focus session.
-- A cycle with no ended_at is still going; whether it is running or waiting comes from its sessions.
ALTER TABLE pomodoro_cycles ADD COLUMN ended_at timestamptz;
