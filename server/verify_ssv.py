"""Verify AdMob rewarded-ad server-side verification (SSV) callbacks.

This is the one check a client-side skipper can't fake. AdMob's servers call your
endpoint only after an ad has actually completed. The callback is signed with
Google's ECDSA keys, so a hooked client can't forge it. Grant rewards here and
never on the client callback alone.

    pip install cryptography requests
    python verify_ssv.py "<full query string AdMob sent you>"

Docs: https://developers.google.com/admob/android/ssv
"""
import base64
import sys
import time
from urllib.parse import parse_qsl

import requests
from cryptography.exceptions import InvalidSignature
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec

KEYS_URL = "https://www.gstatic.com/admob/reward/verifier-keys.json"
MAX_AGE_MS = 60 * 60 * 1000
_seen_transactions: set[str] = set()  # use a database with a unique index in production
_key_cache: dict[int, ec.EllipticCurvePublicKey] = {}


def _b64url(data: str) -> bytes:
    return base64.urlsafe_b64decode(data + "=" * (-len(data) % 4))


def _public_key(key_id: int) -> ec.EllipticCurvePublicKey:
    if key_id not in _key_cache:
        for k in requests.get(KEYS_URL, timeout=10).json()["keys"]:
            _key_cache[k["keyId"]] = serialization.load_pem_public_key(k["pem"].encode())
    return _key_cache[key_id]


def verify(query: str) -> dict:
    """Return the callback params if valid; raise ValueError otherwise."""
    sig_at = query.find("&signature=")
    if sig_at < 0:
        raise ValueError("missing signature")
    message = query[:sig_at].encode()  # signed content is everything before &signature=
    params = dict(parse_qsl(query))

    try:
        _public_key(int(params["key_id"])).verify(
            _b64url(params["signature"]), message, ec.ECDSA(hashes.SHA256())
        )
    except (InvalidSignature, KeyError) as e:
        raise ValueError(f"bad signature: {e!r}")

    if abs(time.time() * 1000 - int(params["timestamp"])) > MAX_AGE_MS:
        raise ValueError("stale callback (possible replay)")
    tx = params["transaction_id"]
    if tx in _seen_transactions:
        raise ValueError("duplicate transaction (replay)")
    _seen_transactions.add(tx)
    return params


if __name__ == "__main__":
    print(verify(sys.argv[1]))
