# Code chuyển tiền ZCoin - Review

Tài liệu này tổng hợp toàn bộ code liên quan đến chuyển tiền (ZCoin) để bạn kiểm tra sai sót.

---

## 1. ZCoinStorage - Lõi lưu trữ

**File:** `src/main/java/com/pokermc/common/config/ZCoinStorage.java`

### getBalance(player)
- Đếm ZCoin rời (ZCoinItem) + ZCoin trong túi (ZCoinBagItem) trong inventory

### add(player, amount)
- Ưu tiên thêm vào ZCoinBag (các slot còn trống)
- Còn dư → tạo ZCoinItem, insertStack vào inventory
- Nếu túi đầy → dropItem xuống đất + gửi message "§e[Túi đầy!] §fX ZC rơi xuống đất - nhặt lên!"

### deduct(player, amount)
- Trừ từ ZCoinBag trước (takeBalance)
- Còn thiếu → trừ từ ZCoinItem (decrement)
- Return false nếu balance < amount

### takeAll(player)
- Lấy toàn bộ ZCoin (dùng khi join bàn Poker/Blackjack)
- setBalance(0) cho bag, setCount(0) cho coin

### giveBack(player, amount)
- Gọi add(player, amount) - trả chip khi rời bàn

---

## 2. ZCoinBagItem - Túi ZCoin

**File:** `src/main/java/com/pokermc/common/item/ZCoinBagItem.java`

- **getBalance(stack)**: Đọc từ CONTAINER component hoặc legacy ZCOIN_BAG_BALANCE
- **addBalance(stack, amount)**: Thêm vào các slot ZCoin trong bag
- **takeBalance(stack, maxTake)**: Trừ từ bag, return số đã lấy
- **setBalance(stack, amount)**: Ghi đè balance

---

## 3. Trade (Item ↔ ZC) - DEPOSIT / WITHDRAW

**Client:** `src/client/java/com/pokermc/common/screen/TradeScreen.java`
- BUY tab: gửi DEPOSIT (amount = số item, data = itemId)
- SELL tab: gửi WITHDRAW (amount = số "đơn vị" rút, data = itemId)

**Server - Poker:** `src/main/java/com/pokermc/poker/network/PokerNetworking.java`

### handleDeposit(be, player, amount, itemId)
1. Kiểm tra itemId trong TRADE_ALLOWED (iron, gold, emerald, diamond)
2. Lấy rate từ config buyRates
3. countItems → removeItems (lấy item khỏi inventory)
4. ZCoinStorage.add(player, toUse * rate)

### handleWithdraw(be, player, amount, itemId)
1. Kiểm tra itemId trong TRADE_ALLOWED
2. Lấy rate, gives từ config sellRates, sellGives
3. totalCost = amount * rate, toGive = amount * gives
4. ZCoinStorage.deduct(player, totalCost) — return false nếu không đủ
5. Tạo ItemStack, insertStack hoặc dropItem nếu đầy

**Server - Blackjack:** `src/main/java/com/pokermc/blackjack/network/BlackjackNetworking.java`
- Logic giống Poker (handleDeposit, handleWithdraw)

---

## 4. Lottery - Mua vé & Trúng thưởng

**Mua vé:** `src/main/java/com/pokermc/xoso/game/XosoGame.java` - buyTicket()
- ZCoinStorage.deduct(player, price)
- Tạo LotteryTicketItem, insertStack hoặc dropItem

**Claim vé:** `src/main/java/com/pokermc/xoso/item/LotteryTicketItem.java` - use()
- stack.decrement(1) — tiêu hủy vé
- ZCoinStorage.add(player, prize) — nếu trúng

---

## 5. Poker - Join/Leave

**Join:** `PokerNetworking.handleJoin` / `handleCreate`
- resolveStartChips = ZCoinStorage.takeAll(player)
- addPlayer(name, chips)

**Leave:** `handleLeave`
- ZCoinStorage.giveBack(player, ps.chips) cho từng player rời

**Offline (kick):** `PokerTableBlockEntity.tick`
- ZCoinStorage.giveBack nếu online
- addToPot(ps.chips) nếu offline

---

## 6. Blackjack - Join/Leave

Tương tự Poker: takeAll khi join, giveBack khi leave.

---

## Điểm cần kiểm tra

1. **Race condition**: deduct + add có atomic không? (Minecraft single-threaded server tick nên thường ổn)
2. **Inventory đầy**: add() đã xử lý drop + message
3. **Số âm**: deduct kiểm tra getBalance < amount trước
4. **Config**: buyRates, sellRates, sellGives — đảm bảo có trong config
5. **Offhand**: countItems/removeItems có đếm offhand (Poker có, Blackjack có)
