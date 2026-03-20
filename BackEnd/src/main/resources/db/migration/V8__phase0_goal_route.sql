ALTER TABLE task_state
    ADD COLUMN IF NOT EXISTS goal_parse_json TEXT,
    ADD COLUMN IF NOT EXISTS skill_route_json TEXT;

