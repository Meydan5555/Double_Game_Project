from datetime import datetime, timezone
from typing import Dict
from uuid import uuid4

from firebase_admin import firestore


class StatsManager:
    """
    This class handles updating and saving match results to the Firestore database.
    When a game ends, it logs the complete match summary and updates both players'
    total wins, losses, and score statistics using an atomic database batch.
    """

    def __init__(self):
        # Connect to the backend Firestore database instance
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
        """
        Saves the finished match details to the 'matches' collection and increments
        the lifetime gaming statistics for each participating player inside the 'users' collection.
        """

        # Generate a unique random ID string for this specific match record
        match_id = str(uuid4())

        player_uids = list(players.keys())

        # Build a dictionary mapping each player's UID to their current display name
        player_names = {
            uid: players[uid].display_name
            for uid in player_uids
        }

        # Structure the complete summary log data map for this game
        match_data = {
            "matchId": match_id,
            "roomCode": room_code,
            "players": player_uids,
            "playerNames": player_names,
            "scores": scores,
            "winnerUid": winner_uid,
            "targetScore": target_score,
            "roundsPlayed": round_number,
            "createdAt": datetime.now(timezone.utc) # Save using standard UTC timestamp clock
        }
    
        # Create a Firestore batch to group multiple database operations into a single transaction
        batch = self.database.batch()

        # Define the target document path inside the 'matches' collection
        match_reference = (
            self.database
            .collection("matches")
            .document(match_id)
        )

        # Queue writing the match log document into our batch transaction
        batch.set(match_reference, match_data)

        # Loop through both competitors to prepare their dynamic stat increments
        for uid in player_uids:
            user_reference = (
                self.database
                .collection("users")
                .document(uid)
            )

            score = scores.get(uid, 0)

            # Prepare baseline atomic increments for games count and combined point tallies
            updates = {
                "gamesPlayed": firestore.Increment(1),
                "totalScore": firestore.Increment(score)
            }

            # Increment wins if the player UID matches the winner, otherwise increment losses
            if uid == winner_uid:
                updates["wins"] = firestore.Increment(1)
            else:
                updates["losses"] = firestore.Increment(1)

            # Queue updating the user profile document, merging fields so it doesn't overwrite unrelated data
            batch.set(
                user_reference,
                updates,
                merge=True
            )

        # Execute all queued match writes and player profile updates together instantly
        batch.commit()

        # Return the generated match ID token back to the server routine
        return match_id
