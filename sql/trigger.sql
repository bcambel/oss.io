-- update column 'updated_at' automatically on update

CREATE OR REPLACE FUNCTION update_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = current_timestamp;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_project_time BEFORE UPDATE ON github_project FOR EACH ROW EXECUTE PROCEDURE update_at_column();
