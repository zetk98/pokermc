# CasinoCraft

Casino mod cho Minecraft - Poker, Blackjack (xì dách) và nhiều game hơn nữa!

## Tính năng

- **Poker** – Chơi Texas Hold'em với bạn bè
- **Blackjack (Xì dách)** – Cược với dealer, luật tùy chỉnh (xì lác, xì bàn)
- **ZCoin** – Tiền tệ trong game để cược và giao dịch
- **ZCoin Bag** – Túi đựng ZCoin với số dư lưu trữ

## Yêu cầu

| Thành phần | Phiên bản |
|------------|-----------|
| Minecraft | 1.21.1 |
| Fabric Loader | ≥ 0.15.0 |
| Fabric API | * |
| Java | ≥ 21 |

## Cài đặt

1. Cài [Fabric Loader](https://fabricmc.net/use/) cho Minecraft 1.21.1
2. Cài [Fabric API](https://modrinth.com/mod/fabric-api)
3. Cài mod CasinoCraft từ file `.jar` hoặc build từ source

## Build từ source

```bash
./gradlew build
```

File JAR được tạo tại: `build/libs/casinocraft-1.0.0.jar`

## Cách chơi

### Poker
- Đặt bàn Poker bằng cách chế tạo `Poker Table`
- Chơi Texas Hold'em với người chơi khác
- Join bàn: đưa ZCoin vào chip để chơi
- Rời bàn: nhận lại ZCoin đủ số chip đang có

### Blackjack
- Đặt bàn Blackjack bằng cách chế tạo `Blackjack Table`
- Cược với dealer hoặc chơi multiplayer
- Luật: xì lác (A + 10/J/Q/K), xì bàn (2 Aces)

### ZCoin
- ZCoin là item tiền tệ dùng để cược
- ZCoin Bag có thể lưu nhiều ZCoin và chuyển số dư giữa các người chơi

## Cấu trúc dự án

```
src/
├── main/java/com/pokermc/     # Logic chính (server)
├── main/resources/            # Assets, lang, config
└── client/java/com/pokermc/   # UI, màn hình (client)
```

## License

MIT License

## Tác giả

ZetDeyNe
