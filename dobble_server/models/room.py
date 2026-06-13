from dataclasses import dataclass, field
from typing import Dict


@dataclass
class Player:
    uid: str
    sid: str
    display_name: str


@dataclass
class Room:
    code: str
    owner_uid: str
    players: Dict[str, Player] = field(default_factory=dict)
    selected_box: str = "original"
    status: str = "waiting"

    def to_dict(self) -> dict:
        return {
            "code": self.code,
            "ownerUid": self.owner_uid,
            "selectedBox": self.selected_box,
            "status": self.status,
            "players": [
                {
                    "uid": player.uid,
                    "displayName": player.display_name,
                }
                for player in self.players.values()
            ],
        }
