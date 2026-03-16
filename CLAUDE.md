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

---

## Stock Exchange (Real-time Trading System)

### Overview
The Stock Exchange is a real-time trading system where players can buy/sell stocks representing various Minecraft resources. Prices update every **5 seconds** for dynamic trading.

### Server-side (`stock/`)

#### StockMarketGame (`game/StockMarketGame.java`)
- **Price Updates**: Every 5 seconds (100 ticks) - real-time updates
- **Price History**: Stores 10 hours of price data (10 bars, 1 per hour)
- **Stock Types**: 15 different stocks across 3 risk tiers (LOW, MEDIUM, HIGH)
- **Market Events**: Random events that affect all stocks (Bull Market, Bear Market, Tech Boom, etc.)

#### Stock Types
- **Blue Chip (LOW_RISK)**: EME (Emerald Mines), DIA (Diamond Corp), RED (Redstone Inc), LAP (Lapis Lazuli), GOL (Gold Mining)
- **Growth (MEDIUM_RISK)**: NETH (Nether Energy), NETR (Nether Tech), ENDQ (End Quartz), ENDR (End Research), NEC (Nether Coal)
- **Speculative (HIGH_RISK)**: OBSI (Obsidian), BSL (Blaze Steel), MAG (Magic Essence), GHO (Ghosts), WIT (Wither Tech)

#### Trading Fees
- Market Orders: 0.5% fee
- Limit Orders: 1% fee

### Client-side GUI (`stock/screen/StockExchangeScreen.java`)

#### Layout
- **GUI Size**: 460×290 pixels, centered on screen
- **Three Panels**: MARKET, DETAIL, PORTFOLIO (tab navigation)

#### MARKET Panel
- **Stock Cards**: 6 per page (2×3 grid), 140×70 pixels each
- **Card Information**:
  - Ticker symbol (colored) - top-left
  - Full name - top-right
  - Current price - middle-left
  - Change % with arrow (▲ green / ▼ red) - bottom-left
  - Mini sparkline (4 bars) - bottom-right (inside borders)
- **Buttons per Card**:
  - `BUY 10` - Quick buy 10 shares of this stock
  - `VIEW >` - Open detail panel for this stock
- **Page Navigation**: `◄ PREV` / `NEXT ►` buttons at bottom, shows "Page X / Y"

#### DETAIL Panel
- **Stock Header**: Ticker, full name, current price, change %
- **10-Hour Price Chart**: Bar chart showing last 10 hours of price data
  - 10 bars, 1 per hour
  - Green bars = price increased, Red bars = price decreased
  - Min/max price labels on left
- **Order Type Buttons**:
  - `MARKET` - Execute immediately at current price
  - `LIMIT BUY` - Set a maximum price to buy at (executes when price ≤ limit)
  - `LIMIT SELL` - Set a minimum price to sell at (executes when price ≥ limit)
- **Quantity Controls**: `-10`, `-1`, display (current qty), `+1`, `+10`, `MAX` buttons
- **Limit Price Controls** (only for LIMIT BUY/SELL): `-5`, `-1`, display (current limit), `+1`, `+5`
- **Action Buttons**:
  - `BUY X` / `SELL X` - Execute market or limit order for X shares
  - `BUY 10` - Quick buy 10 shares at market price
  - `SELL 10` - Quick sell 10 shares at market price
  - `CANCEL ALL` - Cancel all pending limit orders

#### PORTFOLIO Panel
- **Summary**: Total value, Total P/L (profit/loss)
- **Holdings List** (max 3 visible):
  - Ticker, shares owned
  - Current value
  - P/L for this holding
- **Buttons per Holding**:
  - `VIEW` - Open detail panel
  - `SELL ALL` - Sell all shares of this stock at market price

#### Color Scheme
- Background: Dark blue (#1A1A2E)
- Card/Panel backgrounds: Slightly lighter (#16213E)
- Borders: Gold (#D4AF37)
- Profit/Up: Green (#00C853)
- Loss/Down: Red (#FF1744)
- Text: White, Gray for secondary

### Button Functions Explained

| Button | Panel | Function |
|--------|-------|----------|
| MARKET | Top tabs | Switch to market view (see all stocks) |
| DETAIL | Top tabs | Switch to detail view (selected stock) |
| PORTFOLIO | Top tabs | Switch to portfolio view (your holdings) |
| VIEW > | Market card | Open detail panel for this stock |
| BUY 10 | Market card | Quick buy 10 shares at current market price |
| ◄ PREV | Market bottom | Go to previous page of stocks |
| NEXT ► | Market bottom | Go to next page of stocks |
| MARKET | Detail order type | Execute order immediately at current price |
| LIMIT BUY | Detail order type | Place buy order at specified max price |
| LIMIT SELL | Detail order type | Place sell order at specified min price |
| -10/-1/+1/+10 | Detail quantity | Adjust order quantity |
| MAX | Detail quantity | Set quantity to 1000 (default max) |
| -5/-1/+1/+5 | Detail limit price | Adjust limit price (for limit orders) |
| BUY X / SELL X | Detail action | Execute the order with current settings |
| BUY 10 | Detail action | Quick buy 10 shares at market price |
| SELL 10 | Detail action | Quick sell 10 shares at market price |
| CANCEL ALL | Detail action | Cancel all pending limit orders |
| VIEW | Portfolio | Open detail panel for this holding |
| SELL ALL | Portfolio | Sell all shares of this holding |
| ← BACK | Detail | Return to market panel |
| ← BACK TO MARKET | Portfolio | Return to market panel |

### Price Chart Visuals

#### Mini Sparkline (on stock cards)
- 4 bars showing recent price movement
- Each bar = 1 time period (1 hour of compressed data)
- Green = price went up from previous bar
- Red = price went down from previous bar

#### 10-Hour Price Chart (detail panel)
- 10 bars showing complete 10-hour history
- 1 bar per hour
- Height scaled to min/max price in the period
- Color indicates direction from previous hour

