import secrets
from typing import Dict, Optional

from models.room import Player, Room


class RoomManager:
    """
    This class manages all the game rooms currently active on the server.
    It deals with creating new rooms, handling players who want to join or leave,
    generating unique random room codes, and mapping socket session IDs to rooms.
    """

    def __init__(self):
        self.rooms: Dict[str, Room] = {}  # Stores all active rooms (key: room_code, value: Room object)
        self.sid_to_room: Dict[
            str, str] = {}  # Quick lookup map to find a room code by a player's socket session ID (sid)

    def _generate_code(self) -> str:
        """
        Helper method that creates a random, unique 6-digit room code string.
        It uses the secrets module to ensure the code generation is secure.
        """
        for _ in range(100):
            # Generate a secure random number between 100000 and 999999
            code = f"{secrets.randbelow(900000) + 100000}"
            # Return the code immediately if it is not already used by another active room
            if code not in self.rooms:
                return code
        raise RuntimeError("Could not generate a unique room code")

    def create_room(self, uid: str, sid: str, display_name: str) -> Room:
        """
        Creates a brand new game room, generates its code, and adds the creator
        as both the room owner and the first player.
        """
        # Safety check: Prevent a player from creating a room if they are already in one
        if sid in self.sid_to_room:
            raise ValueError("You are already inside a room")

        # Generate the unique code and instantiate the Room object
        code = self._generate_code()
        room = Room(code=code, owner_uid=uid)

        # Add the creator to the room's players list
        room.players[uid] = Player(
            uid=uid,
            sid=sid,
            display_name=display_name,
        )

        # Save the room object and update our socket lookup map
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
        """
        Adds a player to an existing game room after checking that the room exists,
        is waiting for players, is not full, and that the player isn't already inside.
        """
        # Safety Check: Make sure this socket connection isn't already tracked in a room
        if sid in self.sid_to_room:
            raise ValueError("You are already inside a room")

        # Find the requested room by its code
        room = self.rooms.get(code)
        if room is None:
            raise ValueError("Room not found")

        # Validation checks: Can only join if the lobby is waiting and has space
        if room.status != "waiting":
            raise ValueError("The game has already started")
        if len(room.players) >= 2:
            raise ValueError("Room is full")
        if uid in room.players:
            raise ValueError("This user is already in the room")

        # Add the new player to the room dictionary roster
        room.players[uid] = Player(
            uid=uid,
            sid=sid,
            display_name=display_name,
        )

        # Map this socket session ID to the room code
        self.sid_to_room[sid] = code
        return room

    def leave_by_sid(self, sid: str) -> Optional[Room]:
        """
        Removes a player from a room using their socket session ID.
        If the room becomes empty, it deletes the room. If the owner leaves,
        it passes ownership to the remaining player.
        """
        # Remove the session ID from our lookup map and get the room code
        code = self.sid_to_room.pop(sid, None)
        if code is None:
            return None

        room = self.rooms.get(code)
        if room is None:
            return None

        # Find the Firebase user ID (uid) that belongs to this specific socket session ID
        leaving_uid = next(
            (uid for uid, player in room.players.items() if player.sid == sid),
            None,
        )

        # Remove the player from the room roster
        if leaving_uid:
            room.players.pop(leaving_uid, None)

        # If nobody is left in the room, delete the room from the server memory entirely
        if not room.players:
            self.rooms.pop(code, None)
            return None

        # Host Migration Rule: If the host left, pass ownership to the next remaining player
        if room.owner_uid == leaving_uid:
            room.owner_uid = next(iter(room.players))

        return room

    def get_room_for_sid(self, sid: str) -> Optional[Room]:
        """
        Helper method to quickly find the room object that a specific socket session ID belongs to.
        """
        code = self.sid_to_room.get(sid)
        return self.rooms.get(code) if code else None
