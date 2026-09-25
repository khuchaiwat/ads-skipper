"""Simulated attacks against the SSV verifier: forged, tampered, replayed and stale callbacks.

A test keypair stands in for Google's key, so no network access is needed.
Run: python -m pytest server/tests
"""
import base64
import sys
import time
from pathlib import Path

import pytest
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.asymmetric import ec

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import verify_ssv  # noqa: E402

GOOGLE_KEY = ec.generate_private_key(ec.SECP256R1())
ATTACKER_KEY = ec.generate_private_key(ec.SECP256R1())


@pytest.fixture(autouse=True)
def fake_google_key(monkeypatch):
    verify_ssv._seen_transactions.clear()
    monkeypatch.setattr(verify_ssv, "_public_key", lambda _id: GOOGLE_KEY.public_key())


def callback(tx="tx1", ts=None, key=GOOGLE_KEY, reward="100"):
    ts = ts if ts is not None else int(time.time() * 1000)
    msg = (f"ad_network=5450213213286189855&ad_unit=123&reward_amount={reward}"
           f"&reward_item=coins&timestamp={ts}&transaction_id={tx}&user_id=u1")
    sig = key.sign(msg.encode(), ec.ECDSA(hashes.SHA256()))
    return f"{msg}&signature={base64.urlsafe_b64encode(sig).decode().rstrip('=')}&key_id=1"


def test_genuine_callback_accepted():
    assert verify_ssv.verify(callback())["reward_amount"] == "100"


def test_attack_forged_signature():
    with pytest.raises(ValueError, match="signature"):
        verify_ssv.verify(callback(key=ATTACKER_KEY))


def test_attack_tampered_reward_amount():
    with pytest.raises(ValueError, match="signature"):
        verify_ssv.verify(callback().replace("reward_amount=100", "reward_amount=99999"))


def test_attack_missing_signature():
    with pytest.raises(ValueError):
        verify_ssv.verify(callback().split("&signature=")[0])


def test_attack_replay():
    q = callback(tx="same")
    verify_ssv.verify(q)
    with pytest.raises(ValueError, match="duplicate"):
        verify_ssv.verify(q)


def test_attack_stale_callback():
    with pytest.raises(ValueError, match="stale"):
        verify_ssv.verify(callback(ts=int(time.time() * 1000) - 2 * 60 * 60 * 1000))
