# AutoFish 🎣

Client-side Fabric mod: cast your rod once manually, and it automatically reels
the bobber in the moment there's a bite, then casts again — until you cancel it
(switch items or reel early yourself).

Works on any server or in singleplayer. Nothing needed server-side.

## Versions

| Folder | Minecraft | Loader | Fabric API | Java | Mappings |
|---|---|---|---|---|---|
| `fabric-1.21.1/` | 1.21.1 | ≥ 0.16.0 | 0.116.x | 21 | Yarn |
| `fabric-26.3/` | 26.3 | ≥ 0.20.1 | 0.165.x | 25 | none (ship unobfuscated) |

Each folder is a standalone Loom project — `cd` into it and build:

```sh
JAVA_HOME=<matching JDK> gradle build --no-daemon
```

The built jar lands in `build/libs/`. Drop it in your `mods` folder alongside
Fabric API.

## How it works

Every client tick, the mod checks your fishing hook:

- A manual cast (bobber in the water while holding a rod) arms auto mode.
- When the server flags the bite on the synced hook data, the mod uses the rod
  to reel, waits ~1 second, and casts again.
- Auto mode ends if you put the rod away or reel early (right-click pre-bite).

The bite flag has no public getter in either mappings era, so the mod locates
the private synced boolean key once via reflection (falling back to detecting
the sharp downward yank of the bobber if that fails).

## License

MIT — see [LICENSE](LICENSE).
