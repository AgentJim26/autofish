# Dashie's AutoFish (Fabric 1.21.1, client-side)

Cast once manually → auto-reels on bite → auto-recasts. Stops when you cancel.

- MC 1.21.1, Fabric Loader ≥ 0.16, Fabric API, Java 21
- Built jar: `build/libs/autofish-1.0.0.jar`

## Use
1. Hold a fishing rod, cast normally (right-click).
2. Mod takes over: reels the instant the bobber is flagged as bitten, waits ~1s, casts again.
3. Cancel any time by: switching off the rod, or reeling early (right-click before a bite).

## Notes
- Client-side only: works on any server (vanilla/SP/LAN/multiplayer), nothing needed server-side.
- Bite detection reads the server's synced bobber flag via reflection (no public getter in 1.21.1
  Yarn), with bobber-dip motion as fallback.
- Source: `src/client/java/com/dashie/autofish/AutoFishClient.java`
