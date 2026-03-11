# CasinoCraft — Thông số kích thước GUI (pixel)

Tài liệu này liệt kê toàn bộ kích thước GUI, hitbox, nút, v.v. để bạn vẽ lại texture. Sau khi vẽ xong, cung cấp file mới và tôi sẽ cập nhật code cho khớp.

---

## 1. LOBBY SCREENS (Poker, Blackjack, Bang)

**Dùng chung:** `lobby_bg.png` (Poker, Bang) và `lobby_bg_blackjack.png` (Blackjack)

| Thông số | Giá trị (px) |
|----------|--------------|
| **Panel chính** | 260 × 180 |
| **Vị trí** | Căn giữa màn hình |

### Nút Close (✕)
| | px |
|---|-----|
| x | `bgX + BG_W - 20` = 240 (từ trái panel) |
| y | 4 |
| width | 16 |
| height | 14 |

### Nút chính (Create Room / Join / Enter Table / Exchange)
| | px |
|---|-----|
| width | 120 |
| height | 24 |
| x | `(BG_W - 120) / 2` = 70 (từ trái panel) |
| Button 1 y | 75 |
| Button 2 y | 105 (hoặc 102, 130 tùy Blackjack) |

### Vùng text
| Vị trí | y (px) |
|--------|--------|
| Title | 18 |
| Subtitle (BB / players) | 38 |
| "Players:" label | 40 |
| Player list start | 52 |
| ZCoin footer | 164 (BG_H - 16) |

### Khu vực player list (right column)
| | px |
|---|-----|
| colLeft | BG_W - 62 = 198 |
| lineY | 52 |
| Line height | 10 (Poker) / 18 (Blackjack) |
| Max players | 6 |

### Blackjack Lobby (WIP mode)
| | px |
|---|-----|
| Nút "Đóng" | 100 × 20, y = BG_H - 40 = 140 |

### Blackjack Lobby — animation
| | px |
|---|-----|
| TEX_H (total texture height) | 180 × 8 = 1440 |
| NUM_FRAMES | 8 |

### Bang Lobby — player heads
| | px |
|---|-----|
| headSize | 20 |
| startX | 20 |
| startY | 52 |
| Grid layout | 4 cột × 2 hàng |
| headSpacing | 28 (x), 14 (y) |

### Bang Lobby — nút Create (chọn số người)
| | px |
|---|-----|
| Nút 2,4,5,6,7 | 28 × 20 mỗi cái |
| btnY | 55 |
| Vị trí 5 nút (offset từ btnX) | -100, -68, -36, -4, +28 |
| Khoảng cách giữa các nút | 4px |
| Nút "Create" | 120 × 24, y = 85 |

---

## 2. CREATE ROOM SCREENS (Poker, Blackjack)

**Không dùng texture** — vẽ bằng code (fill + border)

| Thông số | Giá trị (px) |
|----------|--------------|
| **Panel** | 300 × 220 |
| **Border ngoài** | 2px |
| **Border trong** | 1px, inset 4px |

### Nút Close
| | px |
|---|-----|
| x | W - 22 = 278 |
| y | 6 |
| width | 16 |
| height | 14 |

### Nút bet level (5 nút)
| | px |
|---|-----|
| width | 48 |
| height | 28 |
| gap | 6 |
| totalW | 5×48 + 4×6 = 264 |
| btnY | 110 |

### Vùng text
| | y (px) |
|---|--------|
| Title | 16 |
| Subtitle | 34 |
| Divider line | 44–45 |
| Hint | 52 |
| Min ZC info | 95 |

---

## 3. TRADE SCREEN (Exchange)

**Không dùng texture** — vẽ bằng code

| Thông số | Giá trị (px) |
|----------|--------------|
| **Panel** | 280 × 210 |
| **Border** | 2px ngoài, 1px trong (inset 4px) |

### Nút tab BUY / SELL
| | px |
|---|-----|
| BUY | x=24, y=28, 50×16 |
| SELL | x=78, y=28, 50×16 |
| Tab highlight | y=44–46, width=50 |

### Item rows (clickable)
| | px |
|---|-----|
| rowY | 52 |
| Row hitbox height | 20 |
| Khoảng cách giữa các row | 22 |
| Row width | W - 16 = 264 |
| Row padding | 8 (x) |

### Amount controls
| | px |
|---|-----|
| − | 20×16 |
| + | 20×16 |
| Max | 36×16 |
| ctrlY | 52 + items×22 + 10 |

### Nút Confirm / Back
| | px |
|---|-----|
| footY | H - 28 = 182 |
| Confirm | 64×18 |
| Back | 64×18 |
| Spacing | 70px giữa 2 nút |

---

## 4. POKER TABLE SCREEN

**Texture:** `poker_table_bg.png`

| Thông số | Giá trị (px) |
|----------|--------------|
| **Table max** | 360 × 230 |
| **Table thực tế** | min(width-20, 360) × min(height-20, 230) |

### Nút
| Nút | width | height | Ghi chú |
|-----|-------|--------|---------|
| Leave | 52 | 13 | Top right |
| Start | 56 | 13 | Bottom center |
| Next Round | 70 | 13 | Bottom center |
| Fold | 40 | 18 | |
| Check | 42 | 18 | |
| Call | 45 | 18 | |
| All In | 44 | 18 | |
| Raise | 40 | 18 | |
| − / + | 12 | 18 | |

### Action bar (btnY)
| | px |
|---|-----|
| btnY | cy + 100 |
| btnH | 18 |

### Card
| | px |
|---|-----|
| CARD_W | 22 |
| CARD_H | 32 |
| SMALL | 14 × 20 |
| ATLAS | 286 × 128 |

### Card spacing
| | px |
|---|-----|
| Community cards | gap 3 |
| Hole cards | gap 3 |

### Oval bàn (8 chỗ ngồi)
| | px |
|---|-----|
| rx (bán kính X) | tw/2 - 22 = 158 |
| ry (bán kính Y) | th/2 - 38 = 77 |
| Góc 8 chỗ (độ) | 90, 45, 0, 315, 270, 225, 180, 135 |
| headSz (avatar) | 14 |
| Empty slot | 28 × 16 |

### Banner / Turn indicator
| | px |
|---|-----|
| Winner banner | 240 × 28 |
| Turn bar | height 12 |

---

## 5. BLACKJACK TABLE SCREEN

**Texture:** `blackjack_table_bg.png`

| Thông số | Giá trị (px) |
|----------|--------------|
| **Table** | 360 × 230 |
| **Card** | 22 × 32 |
| **SMALL** | 14 × 20 |

### Nút
| Nút | width | height |
|-----|-------|--------|
| Leave | 52 | 13 |
| Start | 56 | 13 |
| Hit | 55 | 18 |
| Stand | 55 | 18 |
| Bet | 55 | 18 |
| Confirm | 50 | 16 |
| Dealer Hit | 45 | 18 |
| Solo All | 55 | 18 |

### btnY
| | px |
|---|-----|
| btnY | cy + 95 |

### Solo button
| | px |
|---|-----|
| soloW | 32 |
| soloH | 14 |

---

## 6. BANG TABLE SCREEN

**Texture:** `bang_desert_bg.png` — fullscreen 256×256 (tile)

| Thông số | Giá trị (px) |
|----------|--------------|
| **Card** | 22 × 32 |
| **SLOT** | 22 × 32 |
| **PANEL (player)** | 140 × 81 |
| **PANEL_SLOT** | 22 × 32 |
| **LOG** | 130×34 (3 lines × 10 + 4) |

### Nút
| Nút | width | height |
|-----|-------|--------|
| Leave | 55 | 14 |
| Settings | 60 | 14 |
| End turn | 48 | 14 |
| Discard | 50 | 14 |
| Play | 40 | 14 |
| New Game | 100 | 24 |

### Layout
| | px |
|---|-----|
| pad | 8 |
| btnH | 14 |
| btnGap | 1 |
| logH | 34 |
| Player panel | 6 hand slots + 4 equip slots, gap 1px |

### Player panel hitbox
| | px |
|---|-----|
| Hand | 6×22 + 5×1 + 2 = 139 |
| Equip | 4×22 + 3×1 + 2 = 93 |
| Total | 140 × 81 |

### Role texture
| | px |
|---|-----|
| role | 22 × 32 |
| Path | `bang:textures/roles/split/role_{sheriff|deputy|outlaw|renegade}.png` |

### Bang card texture
| | px |
|---|-----|
| Path | `bang:textures/cards/split/{type}_{rank}{suit}.png` |
| Size | 22 × 32 |

---

## 7. BANG SETTINGS SCREEN

**Không dùng texture** — nền mặc định

| | px |
|---|-----|
| btnW | 80 |
| btnH | 20 |
| gap | 10 |
| Title y | cy - 50 |
| Back button | 80 × 20, y = cy + 40 |

---

## 8. ZCOIN BAG (Vanilla 3×3)

**Texture:** Vanilla `textures/gui/container/dispenser.png` (hoặc tương đương)

| | px |
|---|-----|
| Slot size | 18 × 18 |
| Slot positions | 62 + col×18, 18 + row×18 |
| Grid | 3×3 |
| Panel size | ~176 × 166 (vanilla) |

---

## 9. DANH SÁCH TEXTURE CẦN VẼ

| File | Kích thước | Ghi chú |
|------|------------|---------|
| `casinocraft:textures/gui/lobby_bg.png` | 260 × 180 | Poker, Bang lobby |
| `casinocraft:textures/gui/lobby_bg_blackjack.png` | 260 × 1440 | 8 frames × 180, animation |
| `casinocraft:textures/gui/poker_table_bg.png` | 360 × 230 | Poker table |
| `casinocraft:textures/gui/blackjack_table_bg.png` | 360 × 230 | Blackjack table |
| `casinocraft:textures/gui/bang_desert_bg.png` | 256 × 256 | Tile, fullscreen |
| `casinocraft:textures/gui/card_atlas.png` | 286 × 128 | 52 cards (Poker/Blackjack) |
| `casinocraft:textures/gui/card_back.png` | 22 × 32 | Poker card back |
| `casinocraft:textures/gui/card_back_blackjack.png` | 22 × 32 | Blackjack card back |
| `bang:textures/bang_card_back.png` | 22 × 32 | Bang card back |
| `bang:textures/cards/split/*.png` | 22 × 32 | Mỗi lá bài |
| `bang:textures/roles/split/role_*.png` | 22 × 32 | 4 roles |

---

## 10. GHI CHÚ

- **Tọa độ:** `(0,0)` là góc trên-trái màn hình.
- **Panel:** Thường căn giữa: `bgX = (width - W) / 2`, `bgY = (height - H) / 2`.
- **Draw:** `drawTexture` dùng `(x, y, u, v, w, h, texW, texH)` — nếu texture lớn hơn vùng vẽ, có thể crop.
- **Border:** Vẽ 4 cạnh, độ dày 1–2px.

Sau khi vẽ xong, đặt file vào đúng thư mục và báo tôi — tôi sẽ chỉnh code nếu cần thay đổi kích thước/offset.
