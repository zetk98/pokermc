# Cơ chế ZCoin (ZC) - Logic mới

## 1. Join (vào bàn)

**takeAll** – Lấy toàn bộ ZC trong inventory, đếm và set làm chip của người chơi trong game.

- **Poker WAITING:** takeAll → addPlayer(chips)
- **Poker pending:** Thêm vào hàng chờ, khi vòng mới bắt đầu mới takeAll và add
- **Blackjack:** takeAll → addPlayer(chips)

---

## 2. Out (rời bàn – mọi trường hợp)

**giveBack** – Trả đúng số ZC người đó đang có trong game vào inventory.

| Trường hợp | Xử lý |
|------------|-------|
| Leave (click nút) | giveBack(ps.chips) |
| Đi quá xa (>5 block) | giveBack(ps.chips) |
| Disconnect (còn online) | giveBack(ps.chips) |

---

## 3. Văng game (offline)

**Đẩy chip vào pot** – Người khác có thể thắng.

| Game | Xử lý khi sp == null |
|------|----------------------|
| **Poker** | addToPot(ps.chips) |
| **Blackjack** | addDisconnectChipsToDealer(ps.chips) |

---

## 4. Trở lại bàn / Trận mới

- **Trở lại bàn:** Không cần xử lý đặc biệt.
- **Trận mới:** Giữ nguyên chip, không giveBack/takeAll. Chỉ reset trạng thái ván (bài, cược, v.v.).

---

## 5. Kết thúc ván

Khi ván kết thúc, +/- tiền được xử lý ngay trong `endHand` (Poker) / settlement (Blackjack).
