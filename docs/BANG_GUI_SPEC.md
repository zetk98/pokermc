# Bang! — Đặc tả GUI

## 1. Lá bài (Card)

| Thành phần | Kích thước | Ghi chú |
|------------|------------|---------|
| **Texture lá bài** | 22 × 32 | Kích thước thật của lá |
| **Khung lá bài (frame)** | 24 × 34 | Card nằm trong khung, căn giữa (1px padding mỗi bên) |

- Lá bài 22×32 vẽ **bên trong** khung 24×34, căn giữa.
- Áp dụng cho: hand hero, panel equipment, deck, discard.

---

## 2. Panel người chơi

| Thành phần | Ghi chú |
|------------|---------|
| **Khoảng cách nội dung** | 1px từ viền khung panel đến nội dung bên trong (cả 4 phía) |
| **Kích thước panel** | 153 × 84 |

---

## 3. Hand bài (Hero)

- Không có khung — chỉ vẽ lá bài 22×32.
- Tối đa 12 lá, 6×2 hàng.

---

## 4. Thanh máu (HP)

| Thành phần | Kích thước |
|------------|------------|
| Mỗi segment | 5 × 7 |
| Số segment | 5 |
| Khoảng cách | 1px |

---

## 5. Ô trong panel người chơi

### Layout 2 hàng × 6 ô

**Hàng 1 (6 ô xanh lá):**
- Chưa dùng — vẽ trước cho tương lai (trang bị xanh lá cây)
- Hiện tại để trống

**Hàng 2 (6 ô):**
| Vị trí | Màu | Nội dung |
|--------|-----|----------|
| 0 | Đỏ | Role |
| 1–4 | Xanh dương | Ô trang bị lá xanh dương (súng, Mustang, Barrel, …) |
| 5 | Vàng | Jail — chỉ hiển thị chữ "Jail", không vẽ lá bài |

**Dynamite:** Trang bị vào ô bên cạnh chồng bài giữa bàn, không trang bị lên người chơi.

### Tooltip

| Ô | Ai xem được | Nội dung |
|---|--------------|----------|
| **Đỏ (Role)** | Chỉ Sheriff xem được tooltip của **tất cả** người chơi. Người khác không thấy. | Mô tả role |
| **Các ô còn lại** | Mọi người đều xem được | Tooltip lá (nếu có) |
| **Ô trống** | — | Không hiển thị tooltip |

### Tooltip lá bài (3 dòng)

1. **Tên lá** (vd: BANG, MISSED, VOLCANIC)
2. **Chức năng** (mô tả cách dùng)
3. **Chất + giá trị** (vd: 2♠, AH, 10C)

---

## 6. Animation

Tất cả hành động sau **bắt buộc** có animation:

| Hành động | Mô tả | Trạng thái |
|-----------|-------|------------|
| **Chia bài** | Lá bay từ deck đến hand | ✅ Có (DealAnim) |
| **Lấy bài** | Lá bay từ deck đến hand | ✅ Có (DealAnim) |
| **Hủy bài** | Lá bay từ nguồn đến discard pile | ✅ Có (DiscardFlyAnim) |
| **Bốc bài** | Lá bay từ deck đến hand | ✅ Có (DealAnim) |
| **Trang bị vào ô** | Lá bay từ hand đến ô equipment | ⏳ Cần thêm |
| **Trang bị Dynamite** | Lá bay từ hand đến ô cạnh deck | ⏳ Cần thêm |
| **Tù (Jail)** | Hiệu ứng khi đặt Jail lên mục tiêu | ⏳ Cần thêm |
| **Bắn (Bang)** | Hiệu ứng khi đánh Bang / nhận đạn | ⏳ Cần thêm |
| **Bom nổ (Dynamite)** | Hiệu ứng khi Dynamite nổ | ⏳ Cần thêm |

---

---

## 7. Vị trí Log & Buttons

- Log và toàn bộ button **sát góc dưới phải** nhất có thể (pad = 1px).
- **Discard** và **End turn** luôn hiển thị; khi không hoạt động thì xám (disabled).

---

## 8. Tóm tắt kích thước

| Element | W | H |
|---------|---|---|
| Khung lá bài | 24 | 34 |
| Lá bài (texture) | 22 | 32 |
| Panel | 153 | 84 |
| HP segment | 5 | 7 |
| Log | 152 | 54 |
| Button | 50 | 14 |
