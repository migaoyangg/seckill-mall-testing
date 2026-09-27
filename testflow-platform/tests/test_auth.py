from app.auth import hash_password, verify_password


def test_password_hash_is_salted_and_verifiable():
    first = hash_password("strong-password")
    second = hash_password("strong-password")

    assert first != second
    assert verify_password("strong-password", first)
    assert not verify_password("wrong-password", first)

