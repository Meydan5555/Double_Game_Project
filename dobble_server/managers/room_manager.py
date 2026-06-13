import secrets
from typing import Dict, Optional

from models.room import Player, Room


class RoomManager:
    def __init__(self):
        self.rooms: Dict[str, Room] = {}
        self.sid_to_room: Dict[str, str] = {}

    def _generate_code(self) -> str:
        for _ in range(100):
            code = f"{secrets.randbelow(900000) + 100000}"
            if code not in self.rooms:
                return code
        raise RuntimeError("Could not generate a unique room code")

    def create_room(self, uid: str, sid: str, display_name: str) -> Room:
        if sid in self.sid_to_room:
            raise ValueError("You are already inside a room")

        code = self._generate_code()
        room = Room(code=code, owner_uid=uid)
        room.players[uid] = Player(
            uid=uid,
            sid=sid,
            display_name=display_name,
        )

        self.rooms[code] = room
        self.sid_to_room[sid] = code
        return room

    def join_room(
        self,
        code: str,
        uid: str,
        sid: str,
        display_name: str,
    ) -> Room:
        if sid in self.sid_to_room:
            raise ValueError("You are already inside a room")

        room = self.rooms.get(code)
        if room is None:
            raise ValueError("Room not found")
        if room.status != "waiting":
            raise ValueError("The game has already started")
        if len(room.players) >= 2:
            raise ValueError("Room is full")
        if uid in room.players:
            raise ValueError("This user is already in the room")

        room.players[uid] = Player(
            uid=uid,
            sid=sid,
            display_name=display_name,
        )
        self.sid_to_room[sid] = code
        return room

    def leave_by_sid(self, sid: str) -> Optional[Room]:
        code = self.sid_to_room.pop(sid, None)
        if code is None:
            return None

        room = self.rooms.get(code)
        if room is None:
            return None

        leaving_uid = next(
            (uid for uid, player in room.players.items() if player.sid == sid),
            None,
        )
        if leaving_uid:
            room.players.pop(leaving_uid, None)

        if not room.players:
            self.rooms.pop(code, None)
            return None

        if room.owner_uid == leaving_uid:
            room.owner_uid = next(iter(room.players))

        return room

    def get_room_for_sid(self, sid: str) -> Optional[Room]:
        code = self.sid_to_room.get(sid)
        return self.rooms.get(code) if code else None
