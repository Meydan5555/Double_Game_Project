import json
import time
from pathlib import Path

import socketio


ROOT = Path(__file__).resolve().parent

with (ROOT / "data" / "Cards.json").open("r", encoding="utf-8") as file:
    CARDS = json.load(file)


def unique_symbols(card_id: str) -> set[str]:
    return set(CARDS[str(card_id)].values())


def common_symbol(first_card_id: str, second_card_id: str) -> str:
    common = unique_symbols(first_card_id) & unique_symbols(second_card_id)

    if len(common) != 1:
        raise RuntimeError(
            f"Cards {first_card_id} and {second_card_id} do not share exactly one symbol: {common}"
        )

    return next(iter(common))


class TestPlayer:
    def __init__(self, uid: str, name: str):
        self.uid = uid
        self.name = name
        self.client = socketio.Client(logger=False, engineio_logger=False)
        self.latest_state = None
        self.game_over_data = None
        self.round_results = []
        self.wrong_clicks = 0

        @self.client.on("connected")
        def on_connected(data):
            print(f"{self.name} connected:", data)

        @self.client.on("room_updated")
        def on_room_updated(data):
            print(f"{self.name} room_updated:", data)

        @self.client.on("game_started")
        def on_game_started(data):
            print(f"{self.name} game_started:", data)

        @self.client.on("game_state")
        def on_game_state(data):
            self.latest_state = data
            print(
                f"{self.name} game_state: "
                f"round={data['roundNumber']}, "
                f"center={data['centerCardId']}, "
                f"player={data['playerCardId']}, "
                f"scores={data['scores']}"
            )

        @self.client.on("wrong_symbol")
        def on_wrong_symbol(data):
            self.wrong_clicks += 1
            print(f"{self.name} wrong_symbol:", data)

        @self.client.on("round_result")
        def on_round_result(data):
            self.round_results.append(data)
            print(f"{self.name} round_result:", data)

        @self.client.on("game_over")
        def on_game_over(data):
            self.game_over_data = data
            print(f"{self.name} game_over:", data)

        @self.client.on("game_cancelled")
        def on_game_cancelled(data):
            print(f"{self.name} game_cancelled:", data)

    def connect(self):
        self.client.connect(
            "http://127.0.0.1:5000",
            auth={
                "uid": self.uid,
                "name": self.name,
            },
        )

    def disconnect(self):
        if self.client.connected:
            self.client.disconnect()


def wait_until(predicate, timeout: float = 3.0, message: str = "Timed out"):
    started = time.time()

    while time.time() - started < timeout:
        if predicate():
            return
        time.sleep(0.02)

    raise TimeoutError(message)


def wait_for_new_state(player: TestPlayer, previous_round: int):
    wait_until(
        lambda: (
            player.latest_state is not None
            and player.latest_state["roundNumber"] > previous_round
        ),
        message=f"{player.name} did not receive the next game state",
    )


def click_correct(player: TestPlayer):
    state = player.latest_state
    symbol = common_symbol(
        state["centerCardId"],
        state["playerCardId"],
    )

    response = player.client.call(
        "symbol_click",
        {
            "cardId": state["playerCardId"],
            "symbol": symbol,
        },
    )

    print(f"{player.name} correct click response:", response)
    assert response["ok"] is True
    assert response["correct"] is True


def click_wrong(player: TestPlayer):
    state = player.latest_state
    correct = common_symbol(
        state["centerCardId"],
        state["playerCardId"],
    )

    wrong = next(
        symbol
        for symbol in unique_symbols(state["playerCardId"])
        if symbol != correct
    )

    response = player.client.call(
        "symbol_click",
        {
            "cardId": state["playerCardId"],
            "symbol": wrong,
        },
    )

    print(f"{player.name} wrong click response:", response)
    assert response == {
        "ok": True,
        "correct": False,
    }


shay = TestPlayer("test-user-1", "Shay")
naama = TestPlayer("test-user-2", "Naama")

try:
    shay.connect()

    create_response = shay.client.call(
        "create_room",
        {
            "displayName": "Shay",
        },
    )
    print("create_room:", create_response)
    assert create_response["ok"] is True

    room_code = create_response["room"]["code"]

    naama.connect()

    join_response = naama.client.call(
        "join_room",
        {
            "roomCode": room_code,
            "displayName": "Naama",
        },
    )
    print("join_room:", join_response)
    assert join_response["ok"] is True

    start_response = shay.client.call(
        "start_game",
        {
            "targetScore": 3,
        },
    )
    print("start_game:", start_response)
    assert start_response["ok"] is True

    wait_until(
        lambda: shay.latest_state is not None and naama.latest_state is not None,
        message="Initial game states were not received",
    )

    print("\n--- Testing a wrong click ---")
    click_wrong(naama)

    wait_until(
        lambda: naama.wrong_clicks == 1,
        message="wrong_symbol event was not received",
    )

    print("\n--- Round 1: Shay scores ---")
    previous_round = shay.latest_state["roundNumber"]
    click_correct(shay)
    wait_for_new_state(shay, previous_round)
    wait_for_new_state(naama, previous_round)

    print("\n--- Round 2: Naama scores ---")
    previous_round = naama.latest_state["roundNumber"]
    click_correct(naama)
    wait_for_new_state(shay, previous_round)
    wait_for_new_state(naama, previous_round)

    print("\n--- Round 3: Shay scores ---")
    previous_round = shay.latest_state["roundNumber"]
    click_correct(shay)
    wait_for_new_state(shay, previous_round)
    wait_for_new_state(naama, previous_round)

    print("\n--- Round 4: Naama scores ---")
    previous_round = naama.latest_state["roundNumber"]
    click_correct(naama)
    wait_for_new_state(shay, previous_round)
    wait_for_new_state(naama, previous_round)

    print("\n--- Final round: Shay wins 3-2 ---")
    click_correct(shay)

    wait_until(
        lambda: (
            shay.game_over_data is not None
            and naama.game_over_data is not None
        ),
        message="game_over was not received by both players",
    )

    assert shay.game_over_data["winnerUid"] == shay.uid
    assert shay.game_over_data["scores"][shay.uid] == 3
    assert shay.game_over_data["scores"][naama.uid] == 2
    assert shay.game_over_data["status"] == "finished"

    print("\n========================================")
    print("ALL AUTOMATED SERVER TESTS PASSED")
    print("Wrong click, scoring, rounds and game over are working.")
    print("Final score: Shay 3 - 2 Naama")
    print("========================================")

finally:
    shay.disconnect()
    naama.disconnect()
