from queue import Queue
import sqlite3

# 0 - looking for another player
# 1 - Ready to start the game
# 2 - one person conected
# 3 - waiting for answer
# 4 - got one answer
# 5 - game ended

class GameManager:
    def __init__(self, player1):
        self.status = 0
        self.player1 = player1
        self.player2 = None
        self.deck1 = Queue(maxsize=55)
        self.deck2 = Queue(maxsize=55)

    def get_status(self):
        return self.status

    def found_another_player(self, player2):
        self.player2 = player2
        self.status = 1

    def get_player1(self):
        return self.player1
    
    def get_player2(self):
        return self.player2

    def mix(self):
        conn = sqlite3.connect('CardsDataBase.db')
        cursor = conn.cursor()
        cursor.execute("SELECT * FROM CARDS ORDER BY RANDOM()")

        player1_cards = list()
        player2_cards = list()
        counter = 1

        for row in cursor:
            if counter == 55:
                break
            symbols = list()
            for i in range(1, 9):
                symbols.append(row[i])
            card = Card(symbols)
            if counter % 2 == 1:
                player1_cards.append(card)
                self.deck1.put(card)
            else:
                player2_cards.append(card)
                self.deck2.put(card)
            counter += 1
        
        conn.close()

        return [player1_cards, player2_cards]

    def handle_client(self):
        if self.status == 1:
            
        elif self.status == 2:

        elif self.status == 3:

        elif self.status == 4:

        elif self.status == 5:


    def check_answer(self, player, symbol_id):
        card1 = self.deck1.get()
        card2 = self.deck2.get()

        if player == 1:
            if card2.is_in_card(symbol_id):
                self.deck1.put(card1)
                self.deck1.put(card2)
                return True

            self.deck2.put(card1)
            self.deck2.put(card2)
            return False

        if card1.is_in_card(symbol_id):
            self.deck1.put(card1)
            self.deck1.put(card2)
            return True

        self.deck2.put(card1)
        self.deck2.put(card2)
        return False

    def game_ended(self):
        return self.deck1.empty() or self.deck2.empty()


class Card:
    def __init__(self, symbols):
        self.symbols = symbols

    def is_in_card(self, symbol_id):
        for val in self.symbols:
            if val == symbol_id:
                return True
        return False
