import os
from pathlib import Path
from typing import Any, Dict

import firebase_admin
from firebase_admin import auth, credentials, firestore


class AuthManager:
    """
    This class handles checking player credentials when they try to connect to the server.
    It reads your Firebase credentials file to securely communicate with Firebase Admin SDK,
    verifies the user tokens, and loads their name from the Firestore database.
    """

    def __init__(self, project_root: Path):
        # Read environment variable to see if we should skip security checks for local testing (development mode)
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

        # Find where the Firebase private service key JSON file is stored
        credentials_path = os.getenv(
            "FIREBASE_CREDENTIALS",
            "serviceAccountKey.json",
        )

        credentials_file = Path(credentials_path)

        # Convert relative file path to absolute path if needed
        if not credentials_file.is_absolute():
            credentials_file = project_root / credentials_file

        # Stop the server if the service key file is missing
        if not credentials_file.exists():
            raise FileNotFoundError(
                "Firebase credentials file was not found: "
                f"{credentials_file}"
            )

        # Initialize the Firebase Admin application if it hasn't been started yet
        if not firebase_admin._apps:
            firebase_admin.initialize_app(
                credentials.Certificate(
                    str(credentials_file)
                )
            )

        # Connect to the backend Firestore database instance
        self.database = firestore.client()

    def verify_connection(
            self,
            auth_payload: Any
    ) -> Dict[str, str]:
        """
        Verifies the user token sent by the mobile app during the connection handshake.
        Returns a dictionary containing the user's validated UID, display name, and email.
        """

        # Enforce that the incoming connection data must be a valid dictionary structure
        if not isinstance(auth_payload, dict):
            raise ValueError(
                "Missing Socket.IO authentication payload"
            )

        # Bypass full verification if the server is explicitly set to development bypass mode
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

        # Extract the secure login token string sent by the client
        token = auth_payload.get("token")

        if not isinstance(token, str) or not token.strip():
            raise ValueError(
                "Missing Firebase ID token"
            )

        # Securely verify the token string against the live Firebase servers
        decoded = auth.verify_id_token(token)

        # Extract foundational profile tokens embedded inside the verified token
        uid = decoded["uid"]
        email = decoded.get("email", "")

        display_name = None

        try:
            # Query the users collection document inside Firestore using the verified UID
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

            # Extract user metadata fields if the database record is present
            if document.exists:
                user_data = document.to_dict() or {}

                print(
                    f"Firestore user data for {uid}: "
                    f"{user_data}"
                )

                # Look for fallback name properties inside the database profile layout
                display_name = (
                    user_data.get("displayName")
                    or user_data.get("name")
                    or user_data.get("username")
                )

        except Exception as error:
            # Catch database query errors gracefully to prevent a server crash
            print(
                f"Failed to load Firestore user {uid}: "
                f"{type(error).__name__}: {error}"
            )

        # Clean trailing whitespace from the found user name
        if display_name:
            display_name = str(display_name).strip()

        # Fallback profile hierarchy if the custom Firestore profile database entry is blank
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

        # Return sanitized account credentials map back to the calling connection routine
        return {
            "uid": uid,
            "name": display_name,
            "email": email,
        }
