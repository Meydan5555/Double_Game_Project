from datetime import datetime, timezone
from typing import Dict
from uuid import uuid4

from firebase_admin import firestore


class StatsManager:

    def __init__(self):
        self.database = firestore.client()

    def save_match(
            self,
            room_code: str,
            players: Dict[str, object],
            scores: Dict[str, int],
            winner_uid: str,
            target_score: int,
            round_number: int
    ) -> str:

        match_id = str(uuid4())

        player_uids = list(players.keys())

        player_names = {
            uid: players[uid].display_name
            for uid in player_uids
        }

        match_data = {
            "matchId": match_id,
            "roomCode": room_code,
            "players": player_uids,
            "playerNames": player_names,
            "scores": scores,
            "winnerUid": winner_uid,
            "targetScore": target_score,
            "roundsPlayed": round_number,
            "createdAt": datetime.now(timezone.utc)
        }

        batch = self.database.batch()

        match_reference = (
            self.database
            .collection("matches")
            .document(match_id)
        )

        batch.set(match_reference, match_data)

        for uid in player_uids:
            user_reference = (
                self.database
                .collection("users")
                .document(uid)
            )

            score = scores.get(uid, 0)

            updates = {
                "gamesPlayed": firestore.Increment(1),
                "totalScore": firestore.Increment(score)
            }

            if uid == winner_uid:
                updates["wins"] = firestore.Increment(1)
            else:
                updates["losses"] = firestore.Increment(1)

            batch.set(
                user_reference,
                updates,
                merge=True
            )

        batch.commit()

        return match_id