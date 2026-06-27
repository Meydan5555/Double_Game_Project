import os
from pathlib import Path
from typing import Any, Dict

import socketio
import uvicorn
from dotenv import load_dotenv

# Import our custom manager classes to handle logic modules
from managers.auth_manager import AuthManager
from managers.card_manager import CardManager
from managers.game_manager import GameManager
from managers.room_manager import RoomManager
from managers.stats_manager import StatsManager

# Define the absolute root directory of this server project
ROOT = Path(__file__).resolve().parent
# Load secret credentials and server settings from the .env file
load_dotenv(ROOT / ".env")

# Initialize shared global managers for managing the game database and states
card_manager = CardManager(ROOT / "data" / "Cards.json")
auth_manager = AuthManager(ROOT)
room_manager = RoomManager()
game_manager = GameManager(card_manager)
stats_manager = StatsManager()

# Set up the asynchronous Socket.IO server engine
sio = socketio.AsyncServer(
    async_mode="asgi",  # Web server interface standard for Python async frameworks
    cors_allowed_origins="*",  # Allow connections from any external source or mobile device
    logger=True,  # Turn on server log trackers for tracking custom events
    engineio_logger=True,  # Turn on lower-level network connection engine logs
)

# Wrap the socket server engine inside an ASGI application standard container
app = socketio.ASGIApp(sio)


def success(data: Dict[str, Any] | None = None) -> Dict[str, Any]:
    """
    Helper function that builds a uniform success network response dictionary.
    """
    response: Dict[str, Any] = {"ok": True}
    if data:
        response.update(data)
    return response


def failure(message: str) -> Dict[str, Any]:
    """
    Helper function that builds a uniform failure network response error dictionary.
    """
    return {"ok": False, "error": message}


async def emit_private_game_states(room) -> None:
    """
    Sends the active game board data individually tailored to each active player.
    This guarantees a player only sees their secret card and the open center table card.
    """
    for uid, player in room.players.items():
        # Build the tailored private state map dictionary for this unique user ID
        private_state = game_manager.get_private_state(room, uid)
        # Emit the card data state package specifically to this player's session ID (sid)
        await sio.emit("game_state", private_state, to=player.sid)


@sio.event
async def connect(sid: str, environ: dict, auth: Any):
    """
    Triggered when a mobile device triggers an active network socket connection.
    It verifies the login credential token, stores the user session data, and returns confirmation.
    """
    try:
        # Pass the auth token parameters payload to the auth manager to verify credentials
        user = auth_manager.verify_connection(auth)
    except Exception as error:
        # Disconnect and refuse access if token authentication validation crashes or fails
        print(f"Connection rejected for {sid}: {error}")
        raise socketio.exceptions.ConnectionRefusedError(str(error))

    # Save user credential map properties inside the socket session memory storage
    await sio.save_session(sid, user)

    # Notify the calling phone that they successfully entered the server network environment
    await sio.emit(
        "connected",
        {
            "uid": user["uid"],
            "displayName": user["name"],
            "cardsLoaded": len(card_manager.cards),
        },
        to=sid,
    )
    print(f"Connected: {user['uid']} ({sid})")


@sio.event
async def disconnect(sid: str, reason: str):
    """
    Triggered when a user leaves or drops their active network connection suddenly.
    It closes their active game room and alerts any remaining opponent.
    """
    # Look up what room this disconnected session index belongs to
    room = room_manager.get_room_for_sid(sid)

    if room:
        was_playing = room.status == "playing"
        room_code = room.code

        # Discover which Firebase user ID matched this dropping socket connection
        leaving_user = next(
            (uid for uid, player in room.players.items() if player.sid == sid),
            None,
        )

        # Remove the user record from the active game room registry array layout map
        updated_room = room_manager.leave_by_sid(sid)
        # Remove the tracking channel from the server network layout engine
        await sio.leave_room(sid, room_code)

        # Handle a disconnect that occurs while a match is actively being played
        if was_playing:
            # Terminate and wipe the match state data tracking object block from memory
            game_manager.cancel_game(room_code)

            if updated_room:
                # Revert remaining player to lobby waiting state
                updated_room.status = "waiting"
                # Alert the opponent that the match ended because their rival dropped connection
                await sio.emit(
                    "game_cancelled",
                    {
                        "reason": "opponent_disconnected",
                        "disconnectedUid": leaving_user,
                    },
                    room=room_code,
                )

        # If a player is still in the lobby room, broadcast an updated roster map packet
        if updated_room:
            await sio.emit("room_updated", updated_room.to_dict(), room=room_code)

    print(f"Disconnected: {sid}; reason={reason}")


@sio.event
async def ping_server(sid: str, data: Any = None):
    """
    Simple heartbeat event to check if the server is alive.
    Returns a 'pong' message back to the client.
    """
    return success({"message": "pong", "received": data})


@sio.event
async def create_room(sid: str, data: Any = None):
    """
    Triggered when a player wants to host a new game.
    Generates a fresh room code, places the player inside it, and broadcasts the update.
    """
    try:
        # Retrieve the user profile data saved during the initial connection handshake
        user = await sio.get_session(sid)
        display_name = str(
            user.get("name") or user["uid"]
        ).strip()

        # Ask RoomManager to create the new lobby room
        room = room_manager.create_room(
            uid=user["uid"],
            sid=sid,
            display_name=display_name,
        )
        # Put this socket connection into a virtual room channel named after the room code
        await sio.enter_room(sid, room.code)
        # Tell everyone inside that specific virtual room that the lobby has updated
        await sio.emit("room_updated", room.to_dict(), room=room.code)

        return success({"room": room.to_dict()})
    except Exception as error:
        return failure(str(error))


@sio.event
async def join_room(sid: str, data: Any = None):
    """
    Triggered when a second player enters a 6-digit room code to join an existing lobby.
    Validates the code, adds them to the roster, and alerts the host.
    """
    try:
        # Ensure the incoming data payload contains a proper dictionary mapping
        if not isinstance(data, dict):
            raise ValueError("Expected an object containing roomCode")

        code = str(data.get("roomCode", "")).strip()
        if not code:
            raise ValueError("roomCode is required")

        user = await sio.get_session(sid)
        display_name = str(
            user.get("name") or user["uid"]
        ).strip()

        # Attempt to add the user to the existing room roster structure
        room = room_manager.join_room(
            code=code,
            uid=user["uid"],
            sid=sid,
            display_name=display_name,
        )
        # Connect this socket connection to the virtual room channel broadcast group
        await sio.enter_room(sid, room.code)
        # Inform both competitors in the room that a new player entered the lobby
        await sio.emit("room_updated", room.to_dict(), room=room.code)

        return success({"room": room.to_dict()})
    except Exception as error:
        return failure(str(error))


@sio.event
async def leave_room(sid: str, data: Any = None):
    """
    Triggered when a user clicks the leave button manually.
    Removes them from the room, resets lobby statuses, and notifies the remaining opponent.
    """
    try:
        room = room_manager.get_room_for_sid(sid)
        if room is None:
            raise ValueError("You are not inside a room")

        code = room.code
        # If they left in the middle of a match, delete the active game state instance
        if room.status == "playing":
            game_manager.cancel_game(code)

        # Process the exit removal tracking changes
        updated_room = room_manager.leave_by_sid(sid)
        await sio.leave_room(sid, code)

        if updated_room:
            # Revert any remaining user to a waiting lobby state
            updated_room.status = "waiting"
            # Alert the remaining opponent that the game was closed out early
            await sio.emit("game_cancelled", {"reason": "player_left"}, room=code)
            await sio.emit("room_updated", updated_room.to_dict(), room=code)

        return success()
    except Exception as error:
        return failure(str(error))


@sio.event
async def start_game(sid: str, data: Any = None):
    """
    Triggered by the room owner to kick off the active match.
    Shuffles cards, sets the goal target score, and broadcasts game start signals.
    """
    try:
        room = room_manager.get_room_for_sid(sid)
        if room is None:
            raise ValueError("You are not inside a room")

        user = await sio.get_session(sid)
        # Only the creator/host of the room can trigger the start sequence
        if room.owner_uid != user["uid"]:
            raise ValueError("Only the room owner can start the game")

        payload = data if isinstance(data, dict) else {}
        # Read the winning threshold cap score (defaults to 10 points if missing)
        target_score = int(payload.get("targetScore", 10))

        # Start game engine tracking state variables
        game_manager.start_game(room, target_score=target_score)

        # Inform the mobile apps that gameplay interface frames should load up
        await sio.emit(
            "game_started",
            {
                "roomCode": room.code,
                "targetScore": target_score,
            },
            room=room.code,
        )
        # Send personalized secure card arrays out to each individual user ID
        await emit_private_game_states(room)

        return success()
    except Exception as error:
        return failure(str(error))


@sio.event
async def get_game_state(sid: str, data: Any = None):
    """
    Allows a client to manually request their tailored layout snapshot package.
    Useful for interface refreshes or synchronization loops.
    """
    try:
        room = room_manager.get_room_for_sid(sid)
        if room is None:
            raise ValueError("You are not inside a room")

        user = await sio.get_session(sid)
        state = game_manager.get_private_state(room, user["uid"])
        return success({"game": state})
    except Exception as error:
        return failure(str(error))


@sio.event
async def symbol_click(sid: str, data: Any = None):
    """
    The main game loop logic trigger! Processes a fast matching symbol guess.
    If wrong, it alerts that specific player. If correct, it updates scores,
    shuffles new round cards, and checks for overall win conditions.
    """
    try:
        if not isinstance(data, dict):
            raise ValueError("Expected symbol and cardId")

        symbol = str(data.get("symbol", "")).strip()
        card_id = str(data.get("cardId", "")).strip()

        if not symbol:
            raise ValueError("symbol is required")
        if not card_id:
            raise ValueError("cardId is required")

        room = room_manager.get_room_for_sid(sid)
        if room is None:
            raise ValueError("You are not inside a room")

        user = await sio.get_session(sid)

        # Submit the tapped symbol guess to the core gameplay engine
        correct, result = game_manager.submit_symbol(
            room=room,
            uid=user["uid"],
            symbol=symbol,
            card_id=card_id,
        )

        # Case 1: The user guessed the wrong symbol item
        if not correct:
            # Emit a failure warning event exclusively back to the user who clicked wrongly
            await sio.emit(
                "wrong_symbol",
                {
                    "uid": user["uid"],
                    "submittedSymbol": result["submittedSymbol"],
                },
                to=sid,
            )
            return success({"correct": False})

        # Case 2: The guess is correct! Broadcast round success parameters to the whole room
        await sio.emit(
            "round_result",
            result,
            room=room.code,
        )

        # Check if the correct point addition triggered the match win condition goal
        if result["gameFinished"]:
            game = game_manager.get_game(room.code)
            # Compile final game statistics dashboard logs
            game_over_data = game.to_public_dict(room.players)

            try:
                # Save the complete summary record inside the Firestore database collections
                match_id = stats_manager.save_match(
                    room_code=room.code,
                    players=room.players,
                    scores=game.scores,
                    winner_uid=game.winner_uid,
                    target_score=game.target_score,
                    round_number=game.round_number
                )
                game_over_data["matchId"] = match_id
                game_over_data["statsSaved"] = True

            except Exception as error:
                # Catch writing errors safely to prevent total runtime server lag or crashes
                print("Failed to save match:", error)
                game_over_data["statsSaved"] = False
                game_over_data["statsError"] = str(error)

            # Broadcast the final match over layout summary state to all participants
            await sio.emit(
                "game_over",
                game_over_data,
                room=room.code,
            )
        else:
            # Match is still going on: distribute a new round of cards to each player's screen
            await emit_private_game_states(room)

        return success({"correct": True, **result})
    except Exception as error:
        return failure(str(error))


if __name__ == "__main__":
    # Load configuration environments and spin up the live service deployment frame
    port = int(os.getenv("PORT", "5000"))
    print(f"Loaded and validated {len(card_manager.cards)} Dobble cards.")

    # Run the server framework using Uvicorn on all local channel interfaces
    uvicorn.run("app:app", host="0.0.0.0", port=port, reload=True)
