from app.db import init_db
from app.runner import run_manager


if __name__ == "__main__":
    init_db()
    print("TestFlow worker started. Press Ctrl+C to stop.")
    run_manager.recover_pending()
    run_manager._worker_loop()

