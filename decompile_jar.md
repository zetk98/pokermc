# Khôi phục source từ pokermc-1.0.0.jar

## Bước 1: Tải CFR (decompiler)
- Tải: https://github.com/leibnitz27/cfr/releases
- File: `cfr-0.152.jar` (hoặc bản mới nhất)

## Bước 2: Decompile
Đặt `pokermc-1.0.0.jar` và `cfr-0.152.jar` cùng thư mục, chạy:

```powershell
java -jar cfr-0.152.jar pokermc-1.0.0.jar --outputdir decompiled
```

## Bước 3: Copy source
Sau khi decompile, thư mục `decompiled` sẽ có cấu trúc:
- `com/pokermc/` với các file .java

Copy nội dung từ `decompiled/com/pokermc/` vào `src/` tương ứng.

## Lưu ý
- Code decompile có thể thiếu comment, tên biến có thể khác
- Cần chỉnh lại package/import nếu cần
- Fabric mod: class nằm trong `com.pokermc` (client) và main
