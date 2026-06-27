from dataclasses import dataclass, field
from typing import Dict


@dataclass
class Player:
    """
    This class represents a single player inside the game server.
    It links their Firebase user ID, their active Socket session ID, and their display name.
    """
    uid: str          # Unique Firebase User ID
    sid: str          # Current Socket Session ID (changes every time they reconnect)
    display_name: str # The player's chosen username


@dataclass
class Room:
    """
    This class manages a game lobby room.
    It keeps track of the room code, who owns the room, the list of connected players,
    and whether the room is currently waiting or actively playing.
    """
    code: str
    owner_uid: str     # The Firebase UID of the player who created the room
    players: Dict[str, Player] = field(default_factory=dict) # A dictionary of players inside the room (key: uid, value: Player object)
    selected_box: str = "original" # The chosen theme or deck variation for the game
    status: str = "waiting"        # Room state (e.g., "waiting" for players or "playing")

    def to_dict(self) -> dict:
        """
        Converts the room details and player list into a clean dictionary format.
        This allows the server to easily convert it into a JSON string and send it to the mobile app.
        """
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
                for player in self.players.values() # Loop through all player objects in the dictionary
            ],
        }
