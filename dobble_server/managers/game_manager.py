import random
from typing import Dict, Optional, Tuple

from managers.card_manager import CardManager
from models.game import GameState
from models.room import Room


class GameManager:
    def __init__(self, card_manager: CardManager):
        self.card_manager = card_manager
        self.games: Dict[str, GameState] = {}

    def start_game(self, room: Room, target_score: int = 10) -> GameState:
        if room.code in self.games:
            raise ValueError("A game is already active in this room")
        if len(room.players) != 2:
            raise ValueError("Exactly two players are required to start")
        if target_score < 1 or target_score > 50:
            raise ValueError("targetScore must be between 1 and 50")

        card_order = list(self.card_manager.cards.keys())
        random.shuffle(card_order)

        player_uids = list(room.players.keys())
        state = GameState(
            room_code=room.code,
            card_order=card_order,
            center_index=0,
            player_card_indexes={
                player_uids[0]: 1,
                player_uids[1]: 2,
            },
            scores={uid: 0 for uid in player_uids},
            target_score=target_score,
        )

        self.games[room.code] = state
        room.status = "playing"
        return state

    def get_game(self, room_code: str) -> GameState:
        game = self.games.get(room_code)
        if game is None:
            raise ValueError("No active game in this room")
        return game

    def get_private_state(self, room: Room, uid: str) -> dict:
        game = self.get_game(room.code)
        if uid not in room.players:
            raise ValueError("Player is not in this room")

        result = game.to_public_dict(room.players)
        result["playerCardId"] = game.player_card_id(uid)
        return result

    def submit_symbol(
        self,
        room: Room,
        uid: str,
        symbol: str,
        card_id: str,
    ) -> Tuple[bool, dict]:
        game = self.get_game(room.code)

        if game.winner_uid:
            raise ValueError("The game has already ended")
        if not game.round_open:
            raise ValueError("This round is already closed")
        if uid not in room.players:
            raise ValueError("Player is not in this room")

        expected_player_card = game.player_card_id(uid)
        if str(card_id) != str(expected_player_card):
            raise ValueError("The submitted card does not match the current player card")

        common_symbol = self.card_manager.get_common_symbol(
            game.center_card_id,
            expected_player_card,
        )

        normalized_submitted = symbol.strip().casefold()
        normalized_expected = common_symbol.casefold()

        if normalized_submitted != normalized_expected:
            return False, {
                "correct": False,
                "submittedSymbol": symbol,
                "expectedSymbol": common_symbol,
            }

        game.round_open = False
        game.scores[uid] += 1

        if game.scores[uid] >= game.target_score:
            game.winner_uid = uid
            room.status = "finished"

            return True, {
                "correct": True,
                "winnerUid": uid,
                "correctSymbol": common_symbol,
                "gameFinished": True,
            }

        self._advance_round(game, uid)

        return True, {
            "correct": True,
            "roundWinnerUid": uid,
            "correctSymbol": common_symbol,
            "gameFinished": False,
        }

    def _advance_round(self, game: GameState, round_winner_uid: str) -> None:
        # The winning player's card becomes the next center card.
        new_center_index = game.player_card_indexes[round_winner_uid]
        game.center_index = new_center_index

        used_indexes = {game.center_index}
        for uid in list(game.player_card_indexes.keys()):
            next_index = self._find_next_unused_index(game, used_indexes)
            game.player_card_indexes[uid] = next_index
            used_indexes.add(next_index)

        game.round_number += 1
        game.round_open = True

    def _find_next_unused_index(self, game: GameState, used_indexes: set[int]) -> int:
        total = len(game.card_order)
        start = max(used_indexes) + 1 if used_indexes else 0

        for offset in range(total):
            candidate = (start + offset) % total
            if candidate not in used_indexes:
                return candidate

        raise RuntimeError("No unused card index available")

    def cancel_game(self, room_code: str) -> Optional[GameState]:
        return self.games.pop(room_code, None)
