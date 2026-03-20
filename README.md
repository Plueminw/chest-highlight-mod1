# Chest Highlight Mod

A Fabric mod for Minecraft 1.21.1 that **highlights any chunk containing 5 or more chests** with a glowing orange border outline — visible through walls, spanning the full world height.

## Features
- Server-side chest detection (works in multiplayer/dedicated servers)
- Real-time updates when chests are placed or broken
- Glowing pulsing orange outline rendered client-side
- New players synced with all already-highlighted chunks on join

## Requirements
| Item | Version |
|------|---------|
| Minecraft | 1.21.1 |
| Fabric Loader | ≥ 0.16.14 |
| Fabric API | 0.110.0+1.21.1 |
| Java | 21 |

## Building

### Prerequisites
- JDK 21 installed
- Internet connection (Gradle downloads dependencies)

### Steps
```bash
# Clone / place this folder anywhere
cd chest-highlight-mod

# Build (first run downloads Minecraft, takes a few minutes)
./gradlew build          # Linux / Mac
gradlew.bat build        # Windows
```

The output jar will be at:
```
build/libs/chest-highlight-1.0.0.jar
```

### IntelliJ IDEA setup
```bash
./gradlew genSources idea
```
Then open the project folder in IDEA. If mixins show red, go to  
`Settings → Build → Compiler → Annotation Processors` and enable them.

## Installation
1. Install [Fabric Loader 0.16.14+](https://fabricmc.net/use/installer/) for MC 1.21.1
2. Download [Fabric API 0.110.0+1.21.1](https://modrinth.com/mod/fabric-api) 
3. Put both `fabric-api-*.jar` and `chest-highlight-1.0.0.jar` into your `mods/` folder
4. For servers: same two jars go into the server's `mods/` folder — clients connecting also need the mod installed

## Configuration

Edit `ChestHighlightMod.java` line:
```java
public static final int CHEST_THRESHOLD = 5;
```
Change `5` to any number you like and rebuild.

## How it works

```
Server                              Client
──────────────────────────────────────────────────────
Chunk loads / block placed/broken
  │
  ▼
ChestCounter scans chunk's block entities
  │
  ├─ count >= 5? → add to highlighted set
  │                   │
  │                   ▼
  │               Send HighlightChunkPayload (S2C) ──► Receive packet
  │                                                         │
  │                                                         ▼
  │                                                   Add ChunkPos to
  │                                                   renderer's set
  │                                                         │
  └─ count < 5?  → remove from set                         ▼
                      │                           WorldRenderEvents.LAST
                      ▼                           draws glowing orange
                  Send remove packet ─────────►  outline around chunk
```

## Project Structure

```
src/
├── main/                          ← Server + common code
│   ├── java/com/chesthighlight/
│   │   ├── ChestHighlightMod.java       Main entrypoint & state
│   │   ├── ChestCounter.java            Scans chunks for chests
│   │   ├── ChestBlockListener.java      React to place/break events
│   │   └── network/
│   │       └── ChestHighlightPackets.java  S2C packet definition
│   └── resources/
│       ├── fabric.mod.json
│       └── chesthighlight.mixins.json
└── client/                        ← Client-only rendering code
    └── java/com/chesthighlight/client/
        ├── ChestHighlightClient.java    Client entrypoint & packet receiver
        └── ChestHighlightRenderer.java  OpenGL outline renderer
```
