from sqlalchemy import create_engine, inspect, text

from app import db


def test_existing_run_survives_quality_field_migration(tmp_path, monkeypatch):
    engine = create_engine(f"sqlite:///{tmp_path / 'old.db'}")
    with engine.begin() as connection:
        connection.execute(text("CREATE TABLE test_run (id INTEGER PRIMARY KEY, run_no TEXT, status TEXT)"))
        connection.execute(text("INSERT INTO test_run VALUES (1, 'OLD-RUN', 'FAILED')"))
        connection.execute(text("CREATE TABLE test_case_result (id INTEGER PRIMARY KEY, run_id INTEGER, case_name TEXT)"))
    monkeypatch.setattr(db, "engine", engine)
    db.init_db()
    db.init_db()
    columns = {column["name"] for column in inspect(engine).get_columns("test_run")}
    assert {"selected_nodeids_json", "regression_defect_id", "revision_label"} <= columns
    with engine.connect() as connection:
        row = connection.execute(text("SELECT run_no, selected_nodeids_json FROM test_run WHERE id=1")).one()
        assert row == ("OLD-RUN", "[]")
    engine.dispose()
