import json
from pathlib import Path
from typing import Dict, List, Set


class CardManager:
    VALID_REGIONS = {
        "top-left", "top-center", "top-right",
        "center-left", "center-center", "center-right",
        "bottom-left", "bottom-center", "bottom-right",
    }

    def __init__(self, cards_path: Path):
        with cards_path.open("r", encoding="utf-8") as file:
            self.cards: Dict[str, Dict[str, str]] = json.load(file)

        self._validate()

    def _unique_symbols(self, card: Dict[str, str]) -> Set[str]:
        return {symbol for symbol in card.values() if symbol}

    def _validate(self) -> None:
        if len(self.cards) != 55:
            raise ValueError(f"Expected 55 cards, found {len(self.cards)}")

        for card_id, regions in self.cards.items():
            unknown_regions = set(regions) - self.VALID_REGIONS
            if unknown_regions:
                raise ValueError(
                    f"Card {card_id} contains invalid regions: {sorted(unknown_regions)}"
                )

            symbols = self._unique_symbols(regions)
            if len(symbols) != 8:
                raise ValueError(
                    f"Card {card_id} must contain 8 unique symbols, found {len(symbols)}"
                )

        card_ids = list(self.cards.keys())
        for index, first_id in enumerate(card_ids):
            first_symbols = self._unique_symbols(self.cards[first_id])

            for second_id in card_ids[index + 1:]:
                second_symbols = self._unique_symbols(self.cards[second_id])
                common = first_symbols & second_symbols

                if len(common) != 1:
                    raise ValueError(
                        f"Cards {first_id} and {second_id} share "
                        f"{len(common)} symbols: {sorted(common)}"
                    )

    def get_card(self, card_id: str) -> Dict[str, str]:
        card = self.cards.get(str(card_id))
        if card is None:
            raise KeyError(f"Card {card_id} does not exist")
        return card

    def get_symbols(self, card_id: str) -> List[str]:
        return sorted(self._unique_symbols(self.get_card(card_id)))

    def get_common_symbol(self, first_card_id: str, second_card_id: str) -> str:
        common = (
            set(self.get_symbols(first_card_id))
            & set(self.get_symbols(second_card_id))
        )
        if len(common) != 1:
            raise ValueError("The selected cards do not share exactly one symbol")
        return next(iter(common))
