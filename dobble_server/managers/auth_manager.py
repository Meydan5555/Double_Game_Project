import os
from pathlib import Path
from typing import Any, Dict

import firebase_admin
from firebase_admin import auth, credentials, firestore


class AuthManager:

    def __init__(self, project_root: Path):
        self.dev_skip_auth = (
            os.getenv("DEV_SKIP_AUTH", "false").lower() == "true"
        )

        if self.dev_skip_auth:
            print(
                "WARNING: DEV_SKIP_AUTH is enabled. "
                "Do not use this in production."
            )
            self.database = None
            return

        credentials_path = os.getenv(
            "FIREBASE_CREDENTIALS",
            "serviceAccountKey.json",
        )

        credentials_file = Path(credentials_path)

        if not credentials_file.is_absolute():
            credentials_file = project_root / credentials_file

        if not credentials_file.exists():
            raise FileNotFoundError(
                "Firebase credentials file was not found: "
                f"{credentials_file}"
            )

        if not firebase_admin._apps:
            firebase_admin.initialize_app(
                credentials.Certificate(
                    str(credentials_file)
                )
            )

        # חשוב: מחוץ ל-if, כדי שתמיד יהיה Firestore client
        self.database = firestore.client()

    def verify_connection(
            self,
            auth_payload: Any
    ) -> Dict[str, str]:

        if not isinstance(auth_payload, dict):
            raise ValueError(
                "Missing Socket.IO authentication payload"
            )

        if self.dev_skip_auth:
            uid = str(
                auth_payload.get("uid", "")
            ).strip()

            if not uid:
                raise ValueError(
                    "DEV mode requires auth.uid"
                )

            return {
                "uid": uid,
                "name": str(
                    auth_payload.get("name", uid)
                ),
                "email": str(
                    auth_payload.get("email", "")
                ),
            }

        token = auth_payload.get("token")

        if not isinstance(token, str) or not token.strip():
            raise ValueError(
                "Missing Firebase ID token"
            )

        decoded = auth.verify_id_token(token)

        uid = decoded["uid"]
        email = decoded.get("email", "")

        display_name = None

        try:
            document = (
                self.database
                .collection("users")
                .document(uid)
                .get()
            )

            print(
                f"Firestore user document exists "
                f"for {uid}: {document.exists}"
            )

            if document.exists:
                user_data = document.to_dict() or {}

                print(
                    f"Firestore user data for {uid}: "
                    f"{user_data}"
                )

                display_name = (
                    user_data.get("displayName")
                    or user_data.get("name")
                    or user_data.get("username")
                )

        except Exception as error:
            print(
                f"Failed to load Firestore user {uid}: "
                f"{type(error).__name__}: {error}"
            )

        if display_name:
            display_name = str(display_name).strip()

        if not display_name:
            display_name = (
                decoded.get("name")
                or email
                or uid
            )

        print(
            f"Authenticated user {uid} "
            f"with display name: {display_name}"
        )

        return {
            "uid": uid,
            "name": display_name,
            "email": email,
        }