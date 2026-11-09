# Dashie's AutoFish (Fabric 26.3, client-side)

Cast once manually → auto-reels on bite → auto-recasts. Stops when you cancel.

- MC 26.3 (unobfuscated — no mappings needed), Fabric Loader ≥ 0.20.1, Fabric API, Java 25
- Built jar: `build/libs/autofish-1.0.0.jar`
- Toolchain: Loom 1.19-SNAPSHOT, Gradle ≥ 9.8, JDK 25

## Use
1. Hold a fishing rod, cast normally (right-click).
2. Mod takes over: reels the instant the bite flag syncs, waits ~1s, casts again.
3. Cancel any time by: switching off the rod, or reeling early (right-click before a bite).

## Notes
- Client-side only: works on any server/singleplayer, nothing needed server-side.
- Bite detection reads the server-synced `DATA_BITING` entity-data flag via reflection,
  with hook-dip motion as fallback.
- Source: `src/client/java/com/dashie/autofish/AutoFishClient.java`
- 1.21.1 version lives in `../autofish-mod/` (Yarn mappings, Java 21).
