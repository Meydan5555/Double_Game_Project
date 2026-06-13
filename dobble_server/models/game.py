from dataclasses import dataclass, field
from typing import Dict, List, Optional


@dataclass
class GameState:
    room_code: str
    card_order: List[str]
    center_index: int = 0
    player_card_indexes: Dict[str, int] = field(default_factory=dict)
    scores: Dict[str, int] = field(default_factory=dict)
    round_number: int = 1
    round_open: bool = True
    winner_uid: Optional[str] = None
    target_score: int = 10

    @property
    def center_card_id(self) -> str:
        return self.card_order[self.center_index]

    def player_card_id(self, uid: str) -> str:
        return self.card_order[self.player_card_indexes[uid]]

    def to_public_dict(self, players: Dict[str, object]) -> dict:
        return {
            "roomCode": self.room_code,
            "roundNumber": self.round_number,
            "centerCardId": self.center_card_id,
            "scores": self.scores,
            "targetScore": self.target_score,
            "status": "finished" if self.winner_uid else "playing",
            "winnerUid": self.winner_uid,
            "players": [
                {
                    "uid": uid,
                    "displayName": players[uid].display_name,
                    "score": self.scores.get(uid, 0),
                }
                for uid in players
            ],
        }
