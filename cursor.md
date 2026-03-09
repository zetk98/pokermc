# Cursor - Lưu ý khi làm việc với project

> File này chứa các ghi chú và lưu ý từ người dùng. Đọc file này trước khi trả lời.

## Build JAR

- **Mỗi lần người dùng hỏi** (đặc biệt sau khi sửa code): **phải chạy build để tạo file JAR**.
- Lệnh: `.\gradlew build`
- Output: `build/libs/casinocraft-1.1.0.jar`
- Không quên bước này — người dùng cần file JAR để test mod trong Minecraft.

## Các lưu ý khác

- Game Bang: POV người chơi luôn ở bottom-left.
- 2 người chơi: mỗi người mỗi góc (hero bottom-left, đối thủ top-right).
- Kích thước lá bài chuẩn: 22x32 px.
- Lá bài atlas (cols x rows, mỗi ô 22x32):
  bang 5x5 110x160, barrel 2x1 44x32, beer 3x2 66x64, catbalou 4x1 88x32,
  generalstore 2x1 44x32, jail 3x1 66x32, missed 3x4 66x128, mustang 2x1 44x32,
  panic 4x1 88x32, role 4x1 88x32, schofield 3x1 66x32, stagecoach 2x1 44x32, volcanic 2x1 44x32.
- **Không thêm bớt xóa chỉnh sửa các file texture** — texture do người dùng vẽ, chỉ cập nhật code để map đúng.
- **Texture tách**: Chạy `python split_bang_textures.py` để tách atlas thành từng file `typeId_rankSuit.png` (vd: missed_2S.png, duel_JS.png). Output: `textures/cards/split/`, `textures/roles/split/`. Code ưu tiên texture tách, fallback atlas.
- Roles: 2p=Sheriff+Outlaw, 3p=Sheriff+Outlaw+Deputy, 4p=Sheriff+Deputy+2Outlaws, 5p+=+Renegade.
- Role card: 22x32, 4 roles trong 1 atlas (88x32).
- Mỗi ván 80 lá.
- Thanh máu: chia đốt để dễ nhìn.
- Phát bài: giống Poker, từng lá một có animation.
- GUI: chỉ hiện players + nút Leave, tối đa diện tích. Hiển thị rank+chất trên mỗi lá để check.
