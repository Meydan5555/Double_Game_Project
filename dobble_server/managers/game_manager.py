import random
from typing import Dict, Optional, Tuple

from managers.card_manager import CardManager
from models.game import GameState
from models.room import Room


class GameManager:
    """
    This class handles the active gameplay mechanics for all rooms on the server.
    It deals with starting matches, shuffling card order decks, validating player guesses,
    allocating scores, advancing round indices, and declaring overall winners.
    """

    def __init__(self, card_manager: CardManager):
        self.card_manager = card_manager
        self.games: Dict[
            str, GameState] = {}  # Dict storing active game tracking records (key: roomCode, value: GameState)

    def start_game(self, room: Room, target_score: int = 10) -> GameState:
        """
        Initializes a fresh game state for a lobby room.
        It shuffles the deck order, deals initial cards to both players, sets up scoreboards,
        and switches the room status flag to 'playing'.
        """
        if room.code in self.games:
            raise ValueError("A game is already active in this room")
        # Dobble Game constraint validation check: exactly 2 competitors are required
        if len(room.players) != 2:
            raise ValueError("Exactly two players are required to start")
        if target_score < 1 or target_score > 50:
            raise ValueError("targetScore must be between 1 and 50")

        # Extract all available card identifier keys and randomize their order sequence
        card_order = list(self.card_manager.cards.keys())
        random.shuffle(card_order)

        player_uids = list(room.players.keys())

        # Instantiate a new game tracker layout block
        state = GameState(
            room_code=room.code,
            card_order=card_order,
            center_index=0,  # The deck's very first card index goes to the table center
            player_card_indexes={
                player_uids[0]: 1,  # First player gets card index 1
                player_uids[1]: 2,  # Second player gets card index 2
            },
            scores={uid: 0 for uid in player_uids},  # Initialize scoreboard entries to zero
            target_score=target_score,
        )

        self.games[room.code] = state
        room.status = "playing"  # Flip room network status
        return state

    def get_game(self, room_code: str) -> GameState:
        """
        Helper function to look up an active match block via room code.
        """
        game = self.games.get(room_code)
        if game is None:
            raise ValueError("No active game in this room")
        return game

    def get_private_state(self, room: Room, uid: str) -> dict:
        """
        Constructs the match data package customized for a specific player ID.
        It appends their secret current player card ID to the public game state layout map.
        """
        game = self.get_game(room.code)
        if uid not in room.players:
            raise ValueError("Player is not in this room")

        # Convert the structural core data into a network data map dictionary
        result = game.to_public_dict(room.players)
        # Inject the calling player's specific held card ID property
        result["playerCardId"] = game.player_card_id(uid)
        return result

    def submit_symbol(
            self,
            room: Room,
            uid: str,
            symbol: str,
            card_id: str,
    ) -> Tuple[bool, dict]:
        """
        Processes a player's real-time guess click when they spot a matching symbol.
        Checks if their input matches the mathematical single intersection token between
        their card and the table center.
        """
        game = self.get_game(room.code)

        # Safety filters: stop processing if the match is closed or the round has locked
        if game.winner_uid:
            raise ValueError("The game has already ended")
        if not game.round_open:
            raise ValueError("This round is already closed")
        if uid not in room.players:
            raise ValueError("Player is not in this room")

        # Verify that the player is attempting a guess using the card they actually hold
        expected_player_card = game.player_card_id(uid)
        if str(card_id) != str(expected_player_card):
            raise ValueError("The submitted card does not match the current player card")

        # Ask card manager for the mathematical single match token solution
        common_symbol = self.card_manager.get_common_symbol(
            game.center_card_id,
            expected_player_card,
        )

        # Normalize string cases and whitespaces to prevent typos from failing guesses
        normalized_submitted = symbol.strip().casefold()
        normalized_expected = common_symbol.casefold()

        # Handle incorrect guess outcomes
        if normalized_submitted != normalized_expected:
            return False, {
                "correct": False,
                "submittedSymbol": symbol,
                "expectedSymbol": common_symbol,
            }

        # Handle a correct guess scenario: close round immediately to prevent double score clicks
        game.round_open = False
        game.scores[uid] += 1  # Allocate score point

        # Win Condition Evaluation check: check if player reached the score goal
        if game.scores[uid] >= game.target_score:
            game.winner_uid = uid
            room.status = "finished"

            return True, {
                "correct": True,
                "winnerUid": uid,
                "correctSymbol": common_symbol,
                "gameFinished": True,
            }

        # If match goal wasn't met, shuffle new cards out and update tracking counters
        self._advance_round(game, uid)

        return True, {
            "correct": True,
            "roundWinnerUid": uid,
            "correctSymbol": common_symbol,
            "gameFinished": False,
        }

    def _advance_round(self, game: GameState, round_winner_uid: str) -> None:
        """
        Moves the match to the next round. The card held by the fast round winner
        becomes the fresh center card on the table, and both competitors get new cards.
        """
        # The winning competitor's card position index gets copied to the table center slot
        new_center_index = game.player_card_indexes[round_winner_uid]
        game.center_index = new_center_index

        used_indexes = {game.center_index}

        # Loop through players and deal new unused card indices from the shuffled deck
        for uid in list(game.player_card_indexes.keys()):
            next_index = self._find_next_unused_index(game, used_indexes)
            game.player_card_indexes[uid] = next_index
            used_indexes.add(next_index)  # Log index so the opponent doesn't get it

        game.round_number += 1
        game.round_open = True  # Re-enable matching interaction flags

    def _find_next_unused_index(self, game: GameState, used_indexes: set[int]) -> int:
        """
        Helper lookup function that loops through the shuffled deck registry array to find
        the next item position index that isn't currently placed on the active game table.
        """
        total = len(game.card_order)
        start = max(used_indexes) + 1 if used_indexes else 0

        # Loop over deck elements to discover a free available index location
        for offset in range(total):
            candidate = (start + offset) % total
            if candidate not in used_indexes:
                return candidate

        raise RuntimeError("No unused card index available")

    def cancel_game(self, room_code: str) -> Optional[GameState]:
        """
        Removes an active match tracking block structure from the active tracking registry dict.
        """
        return self.games.pop(room_code, None)
