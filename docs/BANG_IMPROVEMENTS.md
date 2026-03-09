# Bang! Game - Improvements Plan

## 1. Play/Equip + Discard ✅
- Play và Equip hoạt động cả khi hand > limit (có thể đánh/trang bị để giảm bài)
- Xóa text "Discard first" trên nút End turn

## 2. Hand Limit = Current HP ✅
- **Quy tắc**: limit = số máu hiện tại (hp). Character có thể cầm đến 10 lá (chưa implement).
- **Discard**: Nút Bỏ bài hoạt động ngay khi hand > limit (không cần End Turn trước).

## 3. Win Condition + New Game Button ✅
- **Vấn đề**: Khi GAME_OVER không hiển thị rõ ai thắng, không có nút New Game
- **Giải pháp**: 
  - Parse `gameOverWinner` từ state JSON
  - Hiển thị overlay "X wins!" khi GAME_OVER
  - Thêm nút "New Game" khi GAME_OVER, gửi action NEW_GAME

## 4. Gatling Logic Fix ✅
- **Quy tắc**: Gatling = bắn mỗi người 1 Bang. Mỗi người dùng Miss (hoặc Barrel nếu Hearts) để né, không thì nhận 1 damage.
- **Lỗi hiện tại**: Check `p.useBang <= 0 && !hasVolcanic` không áp dụng cho Gatling (Gatling không dùng Bang)
- **Sửa**: Chỉ check `!usedGatlingThisTurn` khi chơi Gatling

## 5. Game Over + New Game Button ✅
- Ẩn các nút khác khi GAME_OVER, chỉ hiển thị nút New Game (100x24) rõ ràng.

## 6. Compact GUI ✅
- Panel player: 140x81, gap 1px. Pad 2px sát viền. Chứa đủ 5 player.

## 7. Cat Balou ✅
- Click equipment slot → discard that specific card (animation bay vào chồng bài bỏ)
- Click panel frame → random discard from hand (animation)
- Luôn vào CHOOSE_CARD, victim chọn

## 8. Animations (TODO)
- **Chia bài**: Tất cả deal (DEALING, DEAL_FIRST, draw 2 đầu lượt) dùng animation lá bay từ deck → hand
- **Bắn**: Khi ai bắn ai, hiệu ứng "bullet" hoặc lá Bang bay từ người bắn → mục tiêu
- **Lấy bài / Hủy bài**: Panic, Cat Balou - lá bay từ hand mục tiêu → hand người lấy hoặc discard pile

## 9. Discard Button ✅
- **Chức năng**: Hủy những lá player chọn, lá đó về chồng bài bỏ (discard pile)
- **Sửa**: Cho phép chọn lá trong phase DISCARD (canInteract bao gồm inDiscardPhase), không xử lý target khi đang discard

## 10. Game End Display ✅
- **Vấn đề**: Khi GAME_OVER chuyển sang BangLobbyScreen nên không thấy overlay kết thúc
- **Sửa**: Giữ màn hình BangTableScreen khi GAME_OVER, hiển thị overlay "X wins!" + nút New Game

## 11. Logic Review ✅
- **advanceGatling**: Vòng lặp đúng - duyệt từ start, bỏ qua source và người chết. Khi hết target → PLAYING.
- **advanceIndians**: Tương tự, không có vòng lặp vô hạn.
- **reactToBang**: Có lastReactionSendTime 300ms lock ở client để tránh spam.
- **prepareNewGame + startGame**: Flow đúng - giữ players, reset state, startGame gán role mới.
