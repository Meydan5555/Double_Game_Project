# Dobble Socket.IO Server

The server currently supports:

- Firebase ID-token authentication
- Temporary local development authentication
- Loading and validating all 55 cards
- Creating, joining and leaving rooms
- Exactly two players per room
- Starting a game
- Random card order
- Private player-card state
- Center card
- Symbol verification on the server
- First valid click wins the round
- Scoring
- Round advancement
- Target score and game completion
- Cancelling an active game after disconnection

## Local installation

```bash
py -m venv .venv
.venv\Scripts\activate
py -m pip install -r requirements.txt
```

Copy `.env.example` to `.env`.

For temporary local testing:

```env
PORT=5000
FIREBASE_CREDENTIALS=serviceAccountKey.json
DEV_SKIP_AUTH=true
```

Run:

```bash
py app.py
```

Then in another terminal:

```bash
.venv\Scripts\activate
py test_client.py
```

## Socket.IO events sent by the client

### create_room

```json
{
  "displayName": "Shay"
}
```

### join_room

```json
{
  "roomCode": "123456",
  "displayName": "Naama"
}
```

### start_game

Only the room owner may use it.

```json
{
  "targetScore": 10
}
```

### get_game_state

No payload is required.

### symbol_click

```json
{
  "cardId": "24",
  "symbol": "Sun"
}
```

### leave_room

No payload is required.

## Events sent by the server

- `connected`
- `room_updated`
- `game_started`
- `game_state`
- `wrong_symbol`
- `round_result`
- `game_over`
- `game_cancelled`

## Firebase setup

1. Open the Firebase project.
2. Open Project settings → Service accounts.
3. Generate a new private key.
4. Save it as `serviceAccountKey.json` in the server folder.
5. Set `DEV_SKIP_AUTH=false`.

Never upload or commit `serviceAccountKey.json`.
