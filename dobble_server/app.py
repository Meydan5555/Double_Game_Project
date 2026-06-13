import os
from pathlib import Path
from typing import Any, Dict

import socketio
import uvicorn
from dotenv import load_dotenv

from managers.auth_manager import AuthManager
from managers.card_manager import CardManager
from managers.game_manager import GameManager
from managers.room_manager import RoomManager
from managers.stats_manager import StatsManager


ROOT = Path(__file__).resolve().parent
load_dotenv(ROOT / ".env")

card_manager = CardManager(ROOT / "data" / "Cards.json")
auth_manager = AuthManager(ROOT)
room_manager = RoomManager()
game_manager = GameManager(card_manager)
stats_manager = StatsManager()

sio = socketio.AsyncServer(
    async_mode="asgi",
    cors_allowed_origins="*",
    logger=True,
    engineio_logger=True,
)

app = socketio.ASGIApp(sio)


def success(data: Dict[str, Any] | None = None) -> Dict[str, Any]:
    response: Dict[str, Any] = {"ok": True}
    if data:
        response.update(data)
    return response


def failure(message: str) -> Dict[str, Any]:
    return {"ok": False, "error": message}


async def emit_private_game_states(room) -> None:
    for uid, player in room.players.items():
        private_state = game_manager.get_private_state(room, uid)
        await sio.emit("game_state", private_state, to=player.sid)


@sio.event
async def connect(sid: str, environ: dict, auth: Any):
    try:
        user = auth_manager.verify_connection(auth)
    except Exception as error:
        print(f"Connection rejected for {sid}: {error}")
        raise socketio.exceptions.ConnectionRefusedError(str(error))

    await sio.save_session(sid, user)
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
    room = room_manager.get_room_for_sid(sid)

    if room:
        was_playing = room.status == "playing"
        room_code = room.code
        leaving_user = next(
            (uid for uid, player in room.players.items() if player.sid == sid),
            None,
        )

        updated_room = room_manager.leave_by_sid(sid)
        await sio.leave_room(sid, room_code)

        if was_playing:
            game_manager.cancel_game(room_code)
            if updated_room:
                updated_room.status = "waiting"
                await sio.emit(
                    "game_cancelled",
                    {
                        "reason": "opponent_disconnected",
                        "disconnectedUid": leaving_user,
                    },
                    room=room_code,
                )

        if updated_room:
            await sio.emit("room_updated", updated_room.to_dict(), room=room_code)

    print(f"Disconnected: {sid}; reason={reason}")


@sio.event
async def ping_server(sid: str, data: Any = None):
    return success({"message": "pong", "received": data})


@sio.event
async def create_room(sid: str, data: Any = None):
    try:
        user = await sio.get_session(sid)
        display_name = str(
            user.get("name") or user["uid"]
        ).strip()

        room = room_manager.create_room(
            uid=user["uid"],
            sid=sid,
            display_name=display_name,
        )
        await sio.enter_room(sid, room.code)
        await sio.emit("room_updated", room.to_dict(), room=room.code)

        return success({"room": room.to_dict()})
    except Exception as error:
        return failure(str(error))


@sio.event
async def join_room(sid: str, data: Any = None):
    try:
        if not isinstance(data, dict):
            raise ValueError("Expected an object containing roomCode")

        code = str(data.get("roomCode", "")).strip()
        if not code:
            raise ValueError("roomCode is required")

        user = await sio.get_session(sid)
        display_name = str(
            user.get("name") or user["uid"]
        ).strip()

        room = room_manager.join_room(
            code=code,
            uid=user["uid"],
            sid=sid,
            display_name=display_name,
        )
        await sio.enter_room(sid, room.code)
        await sio.emit("room_updated", room.to_dict(), room=room.code)

        return success({"room": room.to_dict()})
    except Exception as error:
        return failure(str(error))


@sio.event
async def leave_room(sid: str, data: Any = None):
    try:
        room = room_manager.get_room_for_sid(sid)
        if room is None:
            raise ValueError("You are not inside a room")

        code = room.code
        if room.status == "playing":
            game_manager.cancel_game(code)

        updated_room = room_manager.leave_by_sid(sid)
        await sio.leave_room(sid, code)

        if updated_room:
            updated_room.status = "waiting"
            await sio.emit("game_cancelled", {"reason": "player_left"}, room=code)
            await sio.emit("room_updated", updated_room.to_dict(), room=code)

        return success()
    except Exception as error:
        return failure(str(error))


@sio.event
async def start_game(sid: str, data: Any = None):
    try:
        room = room_manager.get_room_for_sid(sid)
        if room is None:
            raise ValueError("You are not inside a room")

        user = await sio.get_session(sid)
        if room.owner_uid != user["uid"]:
            raise ValueError("Only the room owner can start the game")

        payload = data if isinstance(data, dict) else {}
        target_score = int(payload.get("targetScore", 10))

        game_manager.start_game(room, target_score=target_score)

        await sio.emit(
            "game_started",
            {
                "roomCode": room.code,
                "targetScore": target_score,
            },
            room=room.code,
        )
        await emit_private_game_states(room)

        return success()
    except Exception as error:
        return failure(str(error))


@sio.event
async def get_game_state(sid: str, data: Any = None):
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
        correct, result = game_manager.submit_symbol(
            room=room,
            uid=user["uid"],
            symbol=symbol,
            card_id=card_id,
        )

        if not correct:
            await sio.emit(
                "wrong_symbol",
                {
                    "uid": user["uid"],
                    "submittedSymbol": result["submittedSymbol"],
                },
                to=sid,
            )
            return success({"correct": False})

        await sio.emit(
            "round_result",
            result,
            room=room.code,
        )

        if result["gameFinished"]:
            game = game_manager.get_game(room.code)

            game_over_data = game.to_public_dict(
                room.players
            )

            try:
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
                print("Failed to save match:", error)

                game_over_data["statsSaved"] = False
                game_over_data["statsError"] = str(error)

            await sio.emit(
                "game_over",
                game_over_data,
                room=room.code,
            )
        else:
            await emit_private_game_states(room)

        return success({"correct": True, **result})
    except Exception as error:
        return failure(str(error))


if __name__ == "__main__":
    port = int(os.getenv("PORT", "5000"))
    print(f"Loaded and validated {len(card_manager.cards)} Dobble cards.")
    uvicorn.run("app:app", host="0.0.0.0", port=port, reload=True)
