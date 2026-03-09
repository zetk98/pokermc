"""
Blackjack table top 64x64 - phong cach do hoa pixel.
Nen hong den, chu BLACKJACK.
"""
import struct, zlib, os

BASE = "src/main/resources/assets/casinocraft/textures/block"
W, H = 64, 64

def write_png(path, pixels, w, h):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    raw = b""
    for row in range(h):
        raw += b"\x00"
        for col in range(w):
            r, g, b, a = pixels[row * w + col]
            raw += bytes([r, g, b, a])
    comp = zlib.compress(raw, 9)
    def chunk(tag, data):
        c = struct.pack(">I", len(data)) + tag + data
        return c + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)
    png = (b"\x89PNG\r\n\x1a\n"
           + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
           + chunk(b"IDAT", comp)
           + chunk(b"IEND", b""))
    with open(path, "wb") as f:
        f.write(png)
    print(f"Saved {path} ({w}x{h})")

def px(pixels, x, y, color):
    if 0 <= x < W and 0 <= y < H:
        pixels[y * W + x] = tuple(color)

def fill(pixels, x1, y1, x2, y2, color):
    for y in range(y1, y2):
        for x in range(x1, x2):
            px(pixels, x, y, color)

def border(pixels, x1, y1, x2, y2, color, t=1):
    fill(pixels, x1, y1, x2, y1+t, color)
    fill(pixels, x1, y2-t, x2, y2, color)
    fill(pixels, x1, y1, x1+t, y2, color)
    fill(pixels, x2-t, y1, x2, y2, color)

# Font 3x5 (B,L,A,C,K,J)
FONT = {
    'B':['110','101','110','101','110'],
    'L':['100','100','100','100','111'],
    'A':['010','101','111','101','101'],
    'C':['111','100','100','100','111'],
    'K':['101','110','100','110','101'],
    'J':['011','001','001','101','111'],
}

def draw_char(pixels, cx, cy, ch, color, scale=2):
    pat = FONT.get(ch.upper(), ['000']*5)
    for row, line in enumerate(pat):
        for col, bit in enumerate(line):
            if bit == '1':
                for sy in range(scale):
                    for sx in range(scale):
                        px(pixels, cx + col*scale + sx, cy + row*scale + sy, color)

def draw_text(pixels, x, y, text, color, scale=2, spacing=2):
    cw = 3 * scale
    cx = x
    for ch in text:
        draw_char(pixels, cx, y, ch, color, scale)
        cx += cw + spacing

def main():
    pixels = [(0,0,0,0)] * (W * H)

    # Mau: hong den
    BLACK = (12, 8, 12, 255)
    PINK_DARK = (55, 25, 45, 255)
    PINK_FELT = (95, 40, 70, 255)
    PINK_LIGHT = (140, 60, 100, 255)
    BORDER = (180, 90, 140, 255)
    TEXT = (25, 20, 25, 255)

    # 1. Nen den
    fill(pixels, 0, 0, W, H, BLACK)

    # 2. Vien pixel (4px)
    border(pixels, 0, 0, W, H, BORDER, 4)
    fill(pixels, 4, 4, W-4, H-4, PINK_DARK)

    # 3. Felt hong (vung trong)
    fill(pixels, 8, 8, W-8, H-8, PINK_FELT)
    # Texture nhe - vai pixel sang/toi
    for i in range(12):
        x, y = 12 + (i * 5) % 44, 14 + (i * 4) % 36
        px(pixels, x, y, PINK_LIGHT)
    for i in range(8):
        x, y = 18 + (i * 6) % 40, 22 + (i * 5) % 28
        px(pixels, x, y, (70, 30, 55, 255))

    # 4. Chu BLACKJACK - 2 hang, den
    scale, sp = 2, 2
    cw = 3 * scale
    w1 = 5*cw + 4*sp
    w2 = 4*cw + 3*sp
    draw_text(pixels, (W - w1)//2, 14, "BLACK", TEXT, scale, sp)
    draw_text(pixels, (W - w2)//2, 26, "JACK", TEXT, scale, sp)

    # 5. ZCoin nho (2 dong xu)
    def coin(ox, oy):
        for dy in range(-4, 5):
            for dx in range(-4, 5):
                if dx*dx + dy*dy <= 16:
                    px(pixels, ox+dx, oy+dy, (200, 150, 120, 255))
        px(pixels, ox-1, oy-2, (120, 80, 50, 255))
        px(pixels, ox, oy-2, (120, 80, 50, 255))
        px(pixels, ox+1, oy-2, (120, 80, 50, 255))
        px(pixels, ox-1, oy+2, (120, 80, 50, 255))
        px(pixels, ox, oy+2, (120, 80, 50, 255))
        px(pixels, ox+1, oy+2, (120, 80, 50, 255))
        px(pixels, ox-1, oy, (120, 80, 50, 255))
        px(pixels, ox+1, oy, (120, 80, 50, 255))
    coin(14, 48)
    coin(50, 16)

    write_png(f"{BASE}/blackjack_table_top.png", pixels, W, H)

if __name__ == "__main__":
    main()
