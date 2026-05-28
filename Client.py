import asyncio
import websockets


async def listen_for_messages(websocket):
    """Callback-style loop to handle incoming data."""
    try:
        async for message in websocket:
            print(f"\n[Incoming] {message}")
            print("Your message: ", end="", flush=True)
    except websockets.exceptions.ConnectionClosed:
        print("\nConnection to server lost.")

async def send_messages(websocket):
    """Continuously gets user input and sends it."""
    while True:
        # Using standard input (blocks slightly, but okay for simple scripts)
        loop = asyncio.get_running_loop()
        message = await loop.run_in_executor(None, input, "Your message: ")
        if message.lower() == 'exit':
            break
        try:
            # Ensure the message is UTF-8 encoded
            message = message.encode('utf-8').decode('utf-8')
            await websocket.send(message)
        except UnicodeEncodeError:
            print("Error: Message contains invalid characters and cannot be sent.")

async def main():
    uri = "ws://localhost:8765"
    async with websockets.connect(uri) as websocket:
        print("Connected to the chat server! Type 'exit' to quit.")

        # Run listening and sending tasks concurrently
        await asyncio.gather(
            listen_for_messages(websocket),
            send_messages(websocket)
        )


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        print("\nClient shut down.")
