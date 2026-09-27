from __future__ import annotations

import hashlib
import hmac
import os
import secrets
from datetime import datetime, timedelta
from typing import Callable, Optional

from fastapi import Depends, HTTPException
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from sqlalchemy.orm import Session

from .db import AuthSession, SessionLocal, SystemUser, get_db


security = HTTPBearer(auto_error=False)
PBKDF2_ITERATIONS = 210_000


def hash_password(password: str, salt: bytes | None = None) -> str:
    salt = salt or secrets.token_bytes(16)
    digest = hashlib.pbkdf2_hmac("sha256", password.encode("utf-8"), salt, PBKDF2_ITERATIONS)
    return f"pbkdf2_sha256${PBKDF2_ITERATIONS}${salt.hex()}${digest.hex()}"


def verify_password(password: str, encoded: str) -> bool:
    try:
        algorithm, iterations, salt_hex, digest_hex = encoded.split("$", 3)
        if algorithm != "pbkdf2_sha256":
            return False
        actual = hashlib.pbkdf2_hmac(
            "sha256", password.encode("utf-8"), bytes.fromhex(salt_hex), int(iterations)
        )
        return hmac.compare_digest(actual.hex(), digest_hex)
    except (ValueError, TypeError):
        return False


def token_hash(token: str) -> str:
    return hashlib.sha256(token.encode("utf-8")).hexdigest()


def create_session(db: Session, user: SystemUser, hours: int = 12) -> str:
    token = secrets.token_urlsafe(36)
    db.add(
        AuthSession(
            user_id=user.id,
            token_hash=token_hash(token),
            expires_at=datetime.utcnow() + timedelta(hours=hours),
        )
    )
    db.commit()
    return token


def seed_admin() -> None:
    username = os.getenv("TESTFLOW_ADMIN_USERNAME", "admin")
    password = os.getenv("TESTFLOW_ADMIN_PASSWORD", "testflow123")
    with SessionLocal() as db:
        if db.query(SystemUser).count():
            return
        db.add(
            SystemUser(
                username=username,
                display_name="平台管理员",
                password_hash=hash_password(password),
                role="ADMIN",
            )
        )
        db.commit()


def get_current_user(
    credentials: Optional[HTTPAuthorizationCredentials] = Depends(security),
    db: Session = Depends(get_db),
) -> SystemUser:
    if not credentials:
        raise HTTPException(401, "Authentication required", headers={"WWW-Authenticate": "Bearer"})
    session = (
        db.query(AuthSession)
        .filter(AuthSession.token_hash == token_hash(credentials.credentials), AuthSession.expires_at > datetime.utcnow())
        .first()
    )
    if not session or not session.user.active:
        raise HTTPException(401, "Invalid or expired session", headers={"WWW-Authenticate": "Bearer"})
    return session.user


def authenticate_token(db: Session, token: str) -> Optional[SystemUser]:
    if not token:
        return None
    session = (
        db.query(AuthSession)
        .filter(AuthSession.token_hash == token_hash(token), AuthSession.expires_at > datetime.utcnow())
        .first()
    )
    if not session or not session.user.active:
        return None
    return session.user


def require_roles(*roles: str) -> Callable:
    def dependency(user: SystemUser = Depends(get_current_user)) -> SystemUser:
        if user.role not in roles:
            raise HTTPException(403, "Insufficient permissions")
        return user

    return dependency
