from dataclasses import dataclass, field
from typing import Dict, List, Optional


@dataclass
class GameState:
    """
    This class stores and tracks all the information about an active game room.
    It keeps track of players, scores, cards, and who is currently winning.
    """
    room_code: str
    card_order: List[str]  # The shuffled list of card IDs used for the game
    center_index: int = 0  # The position index of the card currently in the center
    player_card_indexes: Dict[str, int] = field(default_factory=dict)  # Tracks which card index each player has (key: uid, value: card index)
    scores: Dict[str, int] = field(default_factory=dict)               # Tracks current match scores (key: uid, value: score points)
    round_number: int = 1
    round_open: bool = True     # True if players can still guess in this round, False if the round is resetting
    winner_uid: Optional[str] = None  # Stores the user ID of the overall match winner when the game ends
    target_score: int = 10      # The score needed to win the entire game

    @property
    def center_card_id(self) -> str:
        """
        Helper property that returns the actual card ID currently placed in the center.
        """
        return self.card_order[self.center_index]

    def player_card_id(self, uid: str) -> str:
        """
        Helper method that returns the actual card ID currently held by a specific player.
        """
        return self.card_order[self.player_card_indexes[uid]]

    def to_public_dict(self, players: Dict[str, object]) -> dict:
        """
        Converts the game state into a simple dictionary format.
        This dictionary can be safely shared with all players over the network socket.
        """
        return {
            "roomCode": self.room_code,
            "roundNumber": self.round_number,
            "centerCardId": self.center_card_id,
            "scores": self.scores,
            "targetScore": self.target_score,
            "status": "finished" if self.winner_uid else "playing",  # Set status depending on if there is a winner
            "winnerUid": self.winner_uid,
            "players": [
                {
                    "uid": uid,
                    "displayName": players[uid].display_name,  # Get player name from the players list
                    "score": self.scores.get(uid, 0),         # Get player score, defaults to 0 if not found
                }
                for uid in players
            ],
        }
