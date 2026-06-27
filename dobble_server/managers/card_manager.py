import json
from pathlib import Path
from typing import Dict, List, Set


class CardManager:
    """
    This class manages the mathematical deck layout for the Dobble (Spot It!) game.
    It loads the card file, makes sure that every card is placed correctly,
    and runs a special check to guarantee that ANY two cards share EXACTLY one matching symbol.
    """

    # Define the 9 valid position regions allowed on a card layout layout screen
    VALID_REGIONS = {
        "top-left", "top-center", "top-right",
        "center-left", "center-center", "center-right",
        "bottom-left", "bottom-center", "bottom-right",
    }

    def __init__(self, cards_path: Path):
        # Open and load the cards JSON layout metadata data file
        with cards_path.open("r", encoding="utf-8") as file:
            self.cards: Dict[str, Dict[str, str]] = json.load(file)

        # Automatically execute deck rule checks on startup
        self._validate()

    def _unique_symbols(self, card: Dict[str, str]) -> Set[str]:
        """
        Helper method that grabs a card dictionary map and filters out blank spots
        to return a unique set of symbols drawn on it.
        """
        return {symbol for symbol in card.values() if symbol}

    def _validate(self) -> None:
        """
        Strict structural validator function. It ensures the mathematical configuration
        of the Dobble game matches standard deck requirements.
        """
        # Rule 1: A standard deck profile configuration must contain exactly 55 unique cards
        if len(self.cards) != 55:
            raise ValueError(f"Expected 55 cards, found {len(self.cards)}")

        for card_id, regions in self.cards.items():
            # Rule 2: Ensure no typos exist inside the layout position keys
            unknown_regions = set(regions) - self.VALID_REGIONS
            if unknown_regions:
                raise ValueError(
                    f"Card {card_id} contains invalid regions: {sorted(unknown_regions)}"
                )

            # Rule 3: Every card layout item must contain exactly 8 unique symbols
            symbols = self._unique_symbols(regions)
            if len(symbols) != 8:
                raise ValueError(
                    f"Card {card_id} must contain 8 unique symbols, found {len(symbols)}"
                )

        # Rule 4 (The Dobble Rule): Every single card combination pair must share exactly 1 match
        card_ids = list(self.cards.keys())
        for index, first_id in enumerate(card_ids):
            first_symbols = self._unique_symbols(self.cards[first_id])

            # Loop through all remaining cards downstream to compare pairs
            for second_id in card_ids[index + 1:]:
                second_symbols = self._unique_symbols(self.cards[second_id])

                # Find the intersection set containing common symbol matches
                common = first_symbols & second_symbols

                # Fail if two cards contain 0 matches or more than 1 match (breaks game logic)
                if len(common) != 1:
                    raise ValueError(
                        f"Cards {first_id} and {second_id} share "
                        f"{len(common)} symbols: {sorted(common)}"
                    )

    def get_card(self, card_id: str) -> Dict[str, str]:
        """
        Fetches the complete region positioning dictionary map for a specific card ID.
        """
        card = self.cards.get(str(card_id))
        if card is None:
            raise KeyError(f"Card {card_id} does not exist")
        return card

    def get_symbols(self, card_id: str) -> List[str]:
        """
        Returns a clean, sorted list of all unique symbols printed on a given card.
        """
        return sorted(self._unique_symbols(self.get_card(card_id)))

    def get_common_symbol(self, first_card_id: str, second_card_id: str) -> str:
        """
        Calculates and returns the single matching symbol between any two given cards.
        """
        common = (
                set(self.get_symbols(first_card_id))
                & set(self.get_symbols(second_card_id))
        )
        if len(common) != 1:
            raise ValueError("The selected cards do not share exactly one symbol")
        return next(iter(common))  # Extract and return the single matching string item
