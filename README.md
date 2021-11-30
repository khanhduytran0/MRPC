# MRPC
Experimental Discord Mobile Rich Presence (Android)

## How does it work?
It's pretty simple.
- Connect to the [Discord Gateway](https://discord.com/developers/docs/topics/gateway) as a normal Discord Client.
- Send [Identify](https://discord.com/developers/docs/topics/gateway#identifying) and [Update Presence](https://discord.com/developers/docs/topics/gateway#update-presence).

## Notes
- I haven't made this as a background service yet.
- Rate limiting is not yet handled.
- This app uses the Discord Gateway instead of OAuth2 API (which I can't even find the documentation for the `activities.write` scope), so this might not be safe. Use this at your own risk.
- Zlib compression is not yet used, due to the current using library doesn't correctly handle it(?).

## License
[Apache License 2.0](https://github.com/khanhduytran0/MRPC/blob/main/LICENSE).

## Third party libraries and their licenses
- [gson](https://github.com/google/gson): [Apache License 2.0](https://github.com/google/gson/blob/master/LICENSE).
- [Java-WebSocket](https://github.com/TooTallNate/Java-WebSocket): [MIT License](https://github.com/TooTallNate/Java-WebSocket/blob/master/LICENSE).
Note that an old version (1.3.9) is used instead of latest due to the dependence of slf4j. I will update it later.
