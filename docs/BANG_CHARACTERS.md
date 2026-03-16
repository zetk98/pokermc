# Bang! Characters - Bảng thống kê

| # | ID | Tên | Máu | Chức năng | Trạng thái |
|---|-----|-----|-----|-----------|------------|
| 1 | 001 | Paul Regret | 3 | Tầm ngắm của người khác với anh ta +1 khoảng cách (như Mustang) | ✅ Đã code |
| 2 | 002 | El Gringo | 3 | Mỗi khi bị sát thương (kể cả Indians/Gatling), rút 1 lá từ tay người gây sát thương | ✅ Đã code |
| 3 | 003 | Vulture Sam | 4 | Khi 1 nhân vật bị loại, lấy hết bài của người đó | ✅ Đã code |
| 4 | 004 | Calamity Janet | 4 | Có thể dùng BANG như MISS và ngược lại | ✅ Đã code |
| 5 | 005 | Black Jack | 4 | Mở lá thứ 2 khi rút bài. Nếu Cơ hoặc Rô → rút thêm 1 lá | ✅ Đã code |
| 6 | 006 | Willy The Kid | 4 | Bắn bao nhiêu BANG tùy thích | ✅ Đã code |
| 7 | 007 | Lucky Duke | 4 | Khi cần phán xét (Barrel/Jail/Dynamite), mở 2 lá từ xấp, chọn 1 làm kết quả, bỏ cả 2 | ✅ Đã code |
| 8 | 008 | Kit Carlson | 4 | Mỗi lượt rút 3 lá, chọn 2 trong 3 (bỏ 1) | ✅ Đã code |
| 9 | 009 | Rose Doolan | 4 | Tầm ngắm của cô ta tới mọi người -1 (như Appaloosa) | ✅ Đã code |
| 10 | 010 | Suzy Lafayette | 4 | Khi không có bài trên tay → rút 1 lá | ✅ Đã code |
| 11 | 011 | Bart Cassidy | 4 | Mỗi khi mất 1 máu → rút 1 lá | ✅ Đã code |
| 12 | 012 | Jesse Jones | 4 | Mỗi lượt rút bài có thể rút lá đầu tiên trên tay 1 người | ✅ Đã code |
| 13 | 013 | Slab The Killer | 4 | Cần 2 MISS để né được BANG của anh ta | ✅ Đã code |
| 14 | 014 | Sid Ketchum | 4 | Bỏ 2 lá trên tay để hồi 1 máu | ✅ Đã code |
| 15 | 015 | Jourdonnais | 4 | Khi là mục tiêu BANG, rút 1 lá; nếu Cơ → né được | ✅ Đã code |
| 16 | 016 | Pedro Ramirez | 4 | Mỗi lượt rút bài có thể rút lá đầu tiên trên xấp bỏ | ✅ Đã code |

## Quy trình chọn nhân vật

1. Sau khi phát role → **delay 2s** → phase CHARACTER_SELECT
2. Sheriff chọn trước, sau đó theo chiều kim đồng hồ (không delay giữa người)
3. Mỗi người nhận 3 lá character random (không trùng đã chọn)
4. 30 giây để chọn 1 trong 3
5. Sau khi tất cả chọn xong → **delay 2s** → DEALING (chia bài)

## Texture

- `assets/bang/textures/character/001-character.png` đến `016-character.png`
- Mỗi số cố định với nhân vật (001 = Paul Regret, 002 = El Gringo, ...)
