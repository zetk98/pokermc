# Phân tích: Áp dụng LibGui lên toàn bộ GUI

## 1. Kiến trúc hiện tại vs LibGui

### Hiện tại (CasinoCraft)
```
Player right-click block
    → Server gửi OpenTablePayload (JSON state)
    → Client mở Screen tùy chỉnh (extends Screen)
    → Server gửi GameStatePayload (JSON) khi state thay đổi
    → Client gọi updateState(json) để cập nhật
```
- **Không dùng ScreenHandler** cho Poker/Blackjack/Bang
- State truyền qua JSON, không qua slot sync
- Mỗi screen tự vẽ (DrawContext, ButtonWidget)

### LibGui
```
Server tạo ScreenHandler (SyncedGuiDescription)
    → Player mở screen qua ScreenHandlerFactory
    → Client mở CottonInventoryScreen (extends HandledScreen)
    → Sync qua slot / ScreenNetworking
```
- **Bắt buộc ScreenHandler** cho mỗi GUI
- Sync qua inventory slots hoặc custom messages
- Layout dùng WGridPanel, WButton, WLabel...

---

## 2. Danh sách GUI hiện có

| GUI | Loại | Độ phức tạp | Có inventory slots? |
|-----|------|-------------|----------------------|
| PokerLobbyScreen | Lobby | Đơn giản (3 nút) | Không |
| PokerTableScreen | Bàn chơi | Rất cao (cards, chips, animations) | Không |
| CreateRoomScreen | Form | Đơn giản | Không |
| BlackjackLobbyScreen | Lobby | Đơn giản | Không |
| BlackjackTableScreen | Bàn chơi | Cao (cards, bets) | Không |
| CreateBlackjackRoomScreen | Form | Đơn giản | Không |
| BangLobbyScreen | Lobby | Đơn giản | Không |
| BangTableScreen | Bàn chơi | Rất cao (cards, roles, animations) | Không |
| BangSettingsScreen | Form | Đơn giản | Không |
| TradeScreen | Trade | Trung bình (item list, amount) | Không |
| ZCoinBagScreenHandler | Inventory | Đơn giản | **Có** (9 slots) |

---

## 3. Đánh giá: LibGui có phù hợp không?

### ✅ LibGui phù hợp cho
- **ZCoinBagScreenHandler** – Đã có inventory 9 slots, có thể dùng SyncedGuiDescription
- **Lobby screens** (Poker/Blackjack/Bang) – Chỉ có vài nút, có thể dùng WButton
- **CreateRoomScreen, CreateBlackjackRoomScreen** – Form đơn giản
- **BangSettingsScreen** – Form cài đặt

### ❌ LibGui KHÔNG phù hợp cho
- **PokerTableScreen** – Vẽ bài, chips, vị trí người chơi, animation. LibGui không có widget cho game board.
- **BlackjackTableScreen** – Tương tự
- **BangTableScreen** – Rất phức tạp: bài, role, equipment slots, deal animation, discard fly...
- **TradeScreen** – Danh sách item động từ config, chọn amount. LibGui có WItemSlot nhưng Trade không dùng slot thật.

### Vấn đề kiến trúc
Để dùng LibGui, **bắt buộc** phải:
1. Tạo ScreenHandlerType cho mỗi GUI
2. Server mở screen qua `player.openHandledScreen(...)` thay vì gửi packet
3. Client nhận ScreenHandler khi mở

**Hiện tại:** Client mở screen khi nhận OpenTablePayload, không qua ScreenHandler.

**Để migrate:** Phải đổi flow:
- Player right-click → Server mở ScreenHandler (với block pos trong NBT/data)
- Client mở CottonInventoryScreen
- State JSON gửi qua **ScreenNetworking** (LibGui API) thay vì GameStatePayload

---

## 4. Khuyến nghị

### Option A: Không migrate (giữ nguyên)
- Code hiện tại hoạt động ổn
- LibGui thêm dependency, không mang lợi ích rõ ràng cho game board phức tạp

### Option B: Migrate từng phần (khuyến nghị)
1. **ZCoinBagScreenHandler** → LibGui SyncedGuiDescription + CottonInventoryScreen (dễ)
2. **Lobby screens** → Có thể thử, nhưng cần đổi flow mở screen
3. **Table screens** → **Giữ nguyên** – custom drawing là cần thiết

### Option C: Migrate toàn bộ
- **Rủi ro cao**, phải viết lại ~3000+ dòng
- Thay toàn bộ networking flow
- LibGui không có widget cho: card rendering, chip stack, deal animation
- Vẫn phải tự vẽ phần lớn → **không đơn giản hơn**

---

## 5. Kết luận: LibGui có "dễ code" hơn không?

| Loại GUI | Dùng LibGui | Độ dễ |
|----------|-------------|-------|
| Form đơn giản (lobby, create room) | Có thể | Trung bình – phải đổi flow |
| Inventory (ZCoinBag) | Có thể | Dễ |
| Game board (Poker/Blackjack/Bang table) | **Không** | LibGui không hỗ trợ – phải tự vẽ |

**Trả lời:** LibGui **không** làm việc code GUI game board dễ hơn. Nó phù hợp cho form và inventory, không phải game UI tùy chỉnh.

---

## 6. Nếu vẫn muốn dùng LibGui

Chỉ nên migrate **ZCoinBag** (đã có inventory). Các bước:
1. Tạo `ZCoinBagGuiDescription extends SyncedGuiDescription`
2. Đăng ký ScreenHandlerType
3. Tạo `ZCoinBagScreen extends CottonInventoryScreen`
4. ZCoinBagItem mở screen qua ScreenHandlerFactory mới

Các lobby/table screens: **giữ nguyên** code hiện tại.
