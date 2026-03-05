# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**CasinoCraft** — a Fabric mod for Minecraft 1.21.1 implementing Poker (Texas Hold'em), Blackjack (xì dách), and a ZCoin currency system. Mod ID: `casinocraft`. Package root: `com.pokermc`.

## Build & Run Commands

```bash
# Build the mod JAR
./gradlew build
# Output: build/libs/casinocraft-1.0.0.jar

# Run the game client (player 1)
./gradlew runClient

# Run a second client for local 2-player testing (username: TestPlayer2)
./gradlew runClient2

# Generate data (recipes, etc.)
./gradlew runDatagen
```

No lint or test tasks are configured.

## Architecture

### Split Source Sets (Fabric Loom)
- `src/main/java/com/pokermc/` — server-side logic (runs on both sides)
- `src/client/java/com/pokermc/` — client-only UI

### Entry Points
- `PokerMod` — registers all blocks, items, block entity types, and networking payloads
- `PokerModClient` — registers client-side packet receivers that open GUI screens

### Game Logic (server-side, `game/`)
- `PokerGame` — full Texas Hold'em state machine: `WAITING → DEALING → PRE_FLOP → FLOP → TURN → RIVER → SHOWDOWN`. Stored inside `PokerTableBlockEntity`.
- `BlackjackGame` — Blackjack state machine: `WAITING → BETTING → PLAYING → DEALER_SOLO → SETTLEMENT`. Stored inside `BlackjackTableBlockEntity`.
- `Card`, `Deck`, `HandEvaluator`, `HandRank` — card primitives and 5-card hand evaluation

### Block Entities (server-side, `blockentity/`)
- `PokerTableBlockEntity` / `BlackjackTableBlockEntity` — host the game instances; manage a `viewers` set (players with the screen open); implement the `tick()` method which drives: animated card dealing, turn timers (auto-fold on timeout), and proximity checks (force-remove players >5.5 blocks away)
- **NBT persistence is intentionally disabled** — game state resets on server restart

### Networking (`network/`)
Two symmetric pairs of payloads:
- S2C: `OpenTablePayload` (opens the screen), `GameStatePayload` (state updates) — for both Poker and Blackjack
- C2S: `PlayerActionPayload` (poker actions: JOIN/LEAVE/START/RESET/FOLD/CHECK/CALL/RAISE/ALLIN/DEPOSIT/WITHDRAW), `BlackjackActionPayload`

State is serialized as JSON via `serializeState()`. Hole cards are hidden from non-owning players except during SHOWDOWN. All C2S handlers run on `server.execute()`.

### Client Screens (`screen/` in client source set)
- `PokerTableScreen` / `PokerLobbyScreen` — poker UI (table view vs. lobby/waiting)
- `BlackjackTableScreen` / `BlackjackLobbyScreen` — blackjack UI
- `TradeScreen` — exchange items for ZCoin (accessible from lobby screens)
- `CardAnimationHelper` — renders animated card dealing
- Screens receive state via `updateState(stateJson)` called by the packet receivers in `PokerModClient`

### ZCoin Currency (`config/ZCoinStorage`, `item/`)
- ZCoin balance is stored purely in the player's **inventory** (loose `ZCoinItem` stacks + balance inside `ZCoinBagItem` stacks — no file-based wallet)
- `ZCoinStorage` methods: `getBalance`, `add`, `deduct`, `takeAll` (converts all coins to table chips on join), `giveBack` (returns chips when leaving)
- `ZCoinBagItem` uses a Fabric component (`PokerComponents.ZCOIN_BAG_BALANCE`) to store the bag's balance

### Config
- Single config file: `config/casinocraft.json` (auto-created on first run)
- Access via `CasinoCraftConfig.get()` (singleton, lazy-loaded)
- `PokerConfig` is a deprecated shim that delegates to `CasinoCraftConfig`
- Key settings: `smallBlindAmount`, `bigBlindAmount`, `minRaiseAmount`, `turnTimeSeconds`, `minZcToJoin`, `blackjackMaxBet`, `maxPlayers`, and item↔ZCoin exchange rates (`buyRates`, `sellRates`, `sellGives`)

### ZCoin Lifecycle (important rule — see `docs/ZCOIN_MECHANICS.md`)
- **Join**: `takeAll` removes all ZCoin from inventory and sets chip count
- **Leave / kicked / out of range**: `giveBack` returns exact chip count
- **Disconnected (offline)**: chips go to pot (`addToPot`)
- **Between rounds**: chips are preserved; only game state resets
