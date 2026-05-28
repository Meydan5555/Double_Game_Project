import os
from websocket_server import WebsocketServer
from queue import Queue
import sqlite3
import json

rooms = {}
room_counter = 1

# client - first char will be the room id that he is in, second char will be the player number in the game and the third char:
# 0 - requesting decks
# 1 - got answer right
# 2 - got answer wrong
# 
# server:
# {...} - the decks of each player
# 1 - player 1 won the round
# 2 - player 2 won the round
# 3 - game ended player 1 won
# 3 - game ended player 2 won

def new_client(client, server):
    global room_counter
    print(f"Client({client['id']}) connected.")

    for room_id, manager in rooms.items():
        if manager.get_status() == 0:
            manager.found_another_player(client)
            print(f"Client({client['id']}) joined Room {room_id}")

            server.send_message(manager.get_player1, '{"roomId":' + room_id + ', "player": 1}')
            server.send_message(manager.get_player2, '{"roomId":' + room_id + ', "player": 2}')
            return

    rooms[room_counter] = GameManager(client)
    print(f"Room {room_counter} created for Client({client['id']})")
    room_counter += 1

def client_left(client, server):
    if client is None:
        return

    print(f"Client({client['id']}) disconnected.")
    for room_id, clients in list(rooms.items()):
        if client in clients:
            clients.remove(client)
            server.send_message_to_all(f"User {client['id']} left Room {room_id}")
            if not clients:  # Remove empty rooms
                del rooms[room_id]
            break

def message_received(client, server, message):
    try:
        if not message.strip():
            print(f"Client({client['id']}) sent an empty message.")
            return
        if isinstance(message, bytes):
            message = message.decode('utf-8')
        

        return rooms[int(message[0])].handle_client(message)

    except UnicodeDecodeError:
        print(f"Client({client['id']}) sent invalid UTF-8 data.")
    except Exception as e:
        print(f"Unexpected error: {e}")

# Use the PORT environment variable provided by Render, or 8765 locally
PORT = int(os.environ.get("PORT", 8765))

server = WebsocketServer(host='0.0.0.0', port=PORT)
server.set_fn_new_client(new_client)
server.set_fn_client_left(client_left)
server.set_fn_message_received(message_received)

print(f"Server is running on port {PORT}")
server.run_forever()

# 0 - looking for another player
# 1 - Ready to start the game
# 2 - one person conected
# 3 - waiting for answer
# 4 - got one answer
# 5 - game ended

class GameManager:
    def __init__(self, player1):
        self.status = 0
        self.winner_of_round = 0
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

    def handle_client(self, client_message):
        if self.status == 1:
            self.status = 2

        elif self.status == 2:
            self.status = 3
            decks = self.mix()
            message = {}
            message[1] = decks[1]
            message[2] = decks[2]
            message = str(message)
            server.send_message(self.player1, message)
            server.send_message(self.player2, message)
        elif self.status == 3:
            self.status = 4
            if (client_message[1] == 1 and client_message[2] == 1) or (client_message[1] == 2 and client_message[2] == 2):
                self.deck1.put(self.deck2.get())
                self.winner_of_round = 1
            if (client_message[1] == 2 and client_message[2] == 1) or (client_message[1] == 1 and client_message[2] == 2):
                self.deck2.put(self.deck1.get())
                self.winner_of_round = 2

        elif self.status == 4:
            if self.game_ended():
                self.status = 5
                server.send_message(self.player1, str(self.winner_of_round + 2))
                server.send_message(self.player2, str(self.winner_of_round + 2))
            else:
                self.status = 3
                server.send_message(self.player1, str(self.winner_of_round))
                server.send_message(self.player2, str(self.winner_of_round))
            

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
