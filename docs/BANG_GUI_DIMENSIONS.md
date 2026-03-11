# Bang! — Kích thước GUI chuẩn (pixel)

Tài liệu này liệt kê **kích thước cố định** của các thành phần GUI Bang. Chi tiết đầy đủ: `docs/BANG_GUI_SPEC.md`.

**Cập nhật:** Lá bài 22×32 trong khung 24×34; panel 153×84; hand có khung xám; HP 5×4.

---

## 1. BÀI (Card)

| Thành phần | W | H | Ghi chú |
|------------|---|---|---------|
| **Khung lá bài (frame)** | 24 | 34 | Mỗi ô/slot |
| **Lá bài (texture)** | 22 | 32 | Vẽ trong khung, căn giữa (1px padding) |
| **Khoảng cách giữa 2 lá** | 1 | — | slotGap |

**Texture cần vẽ:**
- `bang:textures/cards/split/{type}_{rank}{suit}.png` — 22×32 mỗi lá
- `bang:textures/bang_card_back.png` — 22×32
- `bang:textures/roles/split/role_{sheriff|deputy|outlaw|renegade}.png` — 22×32

---

## 2. PANEL NGƯỜI CHƠI (Player panel)

| Thành phần | W | H | Ghi chú |
|------------|---|---|---------|
| **Panel (hitbox)** | 153 | 84 | Khung chứa tên, HP, hand, equipment |
| **Border** | 1px | — | Viền ngoài |

### Layout bên trong panel

| Vùng | Y (từ đỉnh panel) | Kích thước |
|------|-------------------|------------|
| Tên + HP bar | 3 | 1 dòng text (~8px) |
| Hàng 1 (6 ô xanh lá) | 14 | Chưa dùng — vẽ trước |
| Hàng 2 (role + equip + jail) | 49 | 24×34 × 6 slots, gap 1 |

### HP bar (thanh máu)

| | px |
|---|-----|
| Mỗi segment | 5 × 7 |
| Khoảng cách segment | 1 |
| Số segment | 5 (max HP) |

### Slots trong panel

| Hàng | Slot | Màu | Ghi chú |
|------|------|-----|---------|
| 1 | 6 ô xanh lá | SLOT_GREEN | Chưa dùng |
| 2 | Role (ô 0) | Đỏ | |
| 2 | Equipment (ô 1–4) | Xanh dương | Trang bị đang dùng |
| 2 | Jail (ô 5) | Vàng | Chỉ hiển thị chữ "Jail", không vẽ lá |

**Dynamite:** Ô bên cạnh deck giữa bàn, không trên người chơi.

---

## 3. BÀI TRÊN TAY HERO (Hero hand)

| Thành phần | W | H | Ghi chú |
|------------|---|---|---------|
| **Mỗi lá** | 22 | 32 | Không khung |
| **Cards per row** | 6 | — | Tối đa 12 lá (2 hàng) |
| **Khoảng cách ngang** | 1 | — | cardGap |
| **Khoảng cách dọc** | 2 | — | Giữa 2 hàng |
| **Tổng vùng hand** | 149 | 70 | 6×(24+1)−1 = 149, 2×(34+2)−2 = 70 |

---

## 4. DECK & DISCARD (Giữa màn hình)

| Thành phần | W | H | Ghi chú |
|------------|---|---|---------|
| **Deck** | 22 | 32 | Ô bộ bài |
| **Dynamite slot** | 22 | 32 | Ô thuốc nổ (bên cạnh deck) |
| **Khoảng cách deck–dynamite** | 2 | — | slotGap |

---

## 5. LOG PANEL (Góc phải dưới)

| Thành phần | W | H | Ghi chú |
|------------|---|---|---------|
| **Log** | 151 | 54 | LOG_W × LOG_H |
| **Số dòng** | 5 | — | (54−4)/10 |
| **Chiều cao mỗi dòng** | 10 | — | lineH |
| **Padding text** | 4 | 2 | Trái, trên |
| **Border** | 1px | — | Viền |

---

## 6. NÚT (Buttons)

| Nút | W | H |
|-----|---|---|
| Leave | 50 | 14 |
| Settings | 50 | 14 |
| End turn | 50 | 14 |
| Discard | 50 | 14 |
| Play | 50 | 14 |
| New Game | 100 | 14 |

**Khoảng cách giữa các nút:** 1px (btnGap)

---

## 7. OVERLAY (Jail/Dynamite check, Game Over)

| Thành phần | Ghi chú |
|------------|---------|
| **Lá bài rút (Jail/Dynamite)** | 22×32, vẽ giữa màn |
| **Game Over text** | Căn giữa, không có khung cố định |
| **Notification** | Căn giữa, padding 8px, chiều cao ~16px |

---

## 8. TỔNG HỢP KÍCH THƯỚC CỐ ĐỊNH

| Element | W | H |
|---------|---|---|
| Card (hiển thị) | 24 | 34 |
| Player panel | 153 | 84 |
| Panel slot | 24 | 34 |
| HP segment | 5 | 5 |
| Hero hand area | 149 | 70 |
| Deck / Dynamite | 24 | 34 |
| Log panel | 152 | 54 |
| Button | 50 | 14 |
| Button (New Game) | 100 | 14 |

---

## 9. GHI CHÚ

- **Vị trí** các panel/nút phụ thuộc `width` và `height` màn hình — chỉ kích thước là cố định.
- **Background** (`bang_desert_bg.png`) có thể vẽ bất kỳ kích thước nào (256, 512, 1024…) — sẽ scale lên fullscreen.
- Texture **lá bài** và **role** 22×32 sẽ được scale lên 24×34. Có thể vẽ mới 24×34 để nét hơn.
