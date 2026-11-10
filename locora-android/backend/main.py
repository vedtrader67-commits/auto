import json
import os
import time
from typing import Any

import firebase_admin
from firebase_admin import auth, credentials, db, messaging
from fastapi import FastAPI, Header, HTTPException
from pydantic import BaseModel

SERVICE_ACCOUNT = os.environ.get("FIREBASE_SERVICE_ACCOUNT_JSON", "")
DATABASE_URL = os.environ.get("FIREBASE_DATABASE_URL", "")

if not SERVICE_ACCOUNT or not DATABASE_URL:
    raise RuntimeError("FIREBASE_SERVICE_ACCOUNT_JSON and FIREBASE_DATABASE_URL must be configured")

try:
    service_account_data = json.loads(SERVICE_ACCOUNT)
    credential = credentials.Certificate(service_account_data)
except Exception as exc:
    raise RuntimeError("FIREBASE_SERVICE_ACCOUNT_JSON must contain valid service-account JSON") from exc

firebase_admin.initialize_app(credential, {"databaseURL": DATABASE_URL})
app = FastAPI(title="LOCORA private notification service", version="1.0.0")


class MissingYouRequest(BaseModel):
    pairId: str


def bearer_token(authorization: str | None) -> str:
    if not authorization or not authorization.lower().startswith("bearer "):
        raise HTTPException(status_code=401, detail="Bearer authentication required")
    return authorization.split(" ", 1)[1].strip()


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok", "service": "locora-notifier"}


@app.post("/v1/missing-you")
def missing_you(payload: MissingYouRequest, authorization: str | None = Header(default=None)) -> dict[str, Any]:
    try:
        decoded = auth.verify_id_token(bearer_token(authorization), check_revoked=True)
    except Exception:
        raise HTTPException(status_code=401, detail="Invalid or expired Firebase sign-in")
    uid = decoded.get("uid", "")
    pair_id = payload.pairId.strip()
    if not pair_id or len(pair_id) > 128:
        raise HTTPException(status_code=400, detail="Invalid pair")

    pair_ref = db.reference(f"/pairs/{pair_id}")
    pair = pair_ref.get() or {}
    members = pair.get("members") or {}
    if uid not in members or len(members) != 2:
        raise HTTPException(status_code=403, detail="This account is not in a two-person pair")

    recipient_uid = next((member_uid for member_uid in members.keys() if member_uid != uid), None)
    if not recipient_uid:
        raise HTTPException(status_code=409, detail="Your pair is not complete")

    recipient_presence = (pair.get("presence") or {}).get(recipient_uid) or {}
    last_presence = recipient_presence.get("updatedAt", 0)
    currently_online = recipient_presence.get("state") == "online" and isinstance(last_presence, (int, float)) and (int(time.time() * 1000) - last_presence) < 120000
    if currently_online:
        raise HTTPException(status_code=409, detail="Missing You is available only when your partner is offline")

    cooldown_ref = db.reference(f"/notifyCooldown/{pair_id}/{uid}")
    last_sent = cooldown_ref.get() or 0
    now_ms = int(time.time() * 1000)
    if isinstance(last_sent, (int, float)) and now_ms - int(last_sent) < 60000:
        raise HTTPException(status_code=429, detail="Please wait a minute before sending another signal")

    token_record = db.reference(f"/userTokens/{recipient_uid}").get() or {}
    if token_record.get("pairId") != pair_id or not token_record.get("token"):
        raise HTTPException(status_code=409, detail="Your partner's notification token is not registered yet")

    try:
        message = messaging.Message(
            token=token_record["token"],
            data={"kind": "missing_you"},
            android=messaging.AndroidConfig(priority="high", ttl=60 * 60 * 1000),
        )
        message_id = messaging.send(message)
    except Exception:
        raise HTTPException(status_code=502, detail="Firebase could not accept the notification")

    cooldown_ref.set(now_ms)
    pair_ref.child("missingYou").push({
        "senderUid": uid,
        "createdAt": now_ms,
        "delivery": "accepted",
    })
    return {"ok": True, "messageId": message_id}
