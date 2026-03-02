"""
Bang! Board Game Texture Generator
Run: python generate_bang_textures.py

Output: assets/bang/textures/ (game riêng, không nằm trong casinocraft)

Texture pixel đơn giản, nhìn vào là hiểu:
- 4 role: Sheriff, Outlaw, Vice, Renegade
- ~25 unique action/equipment cards (80 lá có trùng, code tự map)
"""
import struct, zlib, os

BASE = "src/main/resources/assets/bang/textures"
CARD_W, CARD_H = 22, 32

# ---------------------------------------------------------------------------
# Raw PNG writer
# ---------------------------------------------------------------------------
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
    print(f"  {path}  ({w}x{h})")

def new_canvas(w, h, color=(255, 255, 255, 255)):
    return [tuple(color)] * (w * h)

def px(pixels, W, x, y, color):
    if 0 <= x < W and 0 <= y < (len(pixels) // W):
        pixels[y * W + x] = tuple(color)

def fill(pixels, W, x1, y1, x2, y2, color):
    for y in range(y1, y2):
        for x in range(x1, x2):
            px(pixels, W, x, y, color)

def border(pixels, W, x1, y1, x2, y2, color, t=1):
    fill(pixels, W, x1, y1, x2, y1+t, color)
    fill(pixels, W, x1, y2-t, x2, y2, color)
    fill(pixels, W, x1, y1, x1+t, y2, color)
    fill(pixels, W, x2-t, y1, x2, y2, color)

def draw_pattern(pixels, W, cx, cy, pattern, color):
    """pattern: list of strings, '1'=draw"""
    for row, line in enumerate(pattern):
        for col, bit in enumerate(line):
            if bit == '1':
                px(pixels, W, cx + col, cy + row, color)

# ---------------------------------------------------------------------------
# Palette
# ---------------------------------------------------------------------------
WHITE   = (252, 252, 252, 255)
BLACK   = (20, 20, 20, 255)
RED     = (200, 40, 40, 255)
GREEN   = (40, 160, 60, 255)
GOLD    = (212, 175, 55, 255)
BROWN   = (120, 80, 40, 255)
GRAY    = (100, 100, 110, 255)
LIGHT   = (220, 220, 220, 255)

# ---------------------------------------------------------------------------
# 4 ROLE CARDS — Sheriff, Outlaw, Vice, Renegade
# ---------------------------------------------------------------------------
ROLES = {
    "sheriff":  ["00100", "01110", "11111", "01110", "00100"],  # badge/star
    "outlaw":   ["01110", "10101", "01110", "00100", "01010"],  # skull
    "vice":     ["11111", "10001", "10001", "10001", "11111"],  # shield
    "renegade": ["10101", "01010", "10101", "01010", "10101"],  # two faces
}

def make_role_card(role_id, pattern):
    pixels = new_canvas(CARD_W, CARD_H, WHITE)
    border(pixels, CARD_W, 0, 0, CARD_W, CARD_H, GRAY, 1)
    cx, cy = (CARD_W - 5) // 2, (CARD_H - 5) // 2 - 2
    draw_pattern(pixels, CARD_W, cx, cy, pattern, BLACK)
    return pixels

# ---------------------------------------------------------------------------
# ACTION CARDS (unique symbols only)
# ---------------------------------------------------------------------------
# Bang! = đạn (bullet)
BANG = ["00100", "01110", "11111", "01110", "00100"]  # bullet

# Miss = né
MISS = ["10101", "01010", "10101", "01010", "10101"]  # X

# Beer = +1 máu (green heart)
BEER = ["01010", "11111", "11111", "01110", "00100"]  # heart

# +2 (rút 2 lá): + và 2 khung
DRAW2 = ["00100", "01110", "11111", "01110", "00100"]  # + center

# +3 (rút 3 lá)
DRAW3 = ["00100", "01110", "11111", "01110", "00100"]

# Gatling (bắn cả bàn - 3 đạn)
GATLING = ["010", "010", "111", "010", "010"]  # 3 bullets in row

# Saloon (+1 all)
SALOON = ["01010", "11111", "11111", "01110", "00100"]  # heart

# Panico (rút bài 1 người) = ...
PANICO_DOTS = ["10101", "01010", "10101"]  # ...

# Cat Balou (hủy)
CATBALOU = ["01110", "10001", "10001", "01110", "00000"]  # discard

# Duello (đấu súng)
DUELLO = ["10001", "01010", "00100", "01010", "10001"]  # crossed

# Guns 1-5
GUN = ["10001", "01010", "00100", "01010", "10001"]  # crosshair

# Mustang (ngựa +1 tầm né)
MUSTANG = ["01010", "11111", "11111", "01110", "00100"]  # horse head

# Appaloosa (ngựa -1 tầm đánh)
APPALOOSA = ["01010", "11111", "11111", "01110", "00100"]

# Dynamite (boom)
DYNAMITE = ["00100", "01110", "11111", "01110", "00100"]  # bullet/explosion

# Prison (nhốt tù)
PRISON = ["11111", "10001", "10001", "10001", "11111"]  # bars

# Indiani
INDIANI = ["00100", "01110", "11111", "01010", "00100"]  # feather

# General Store
GENERAL = ["00100", "01110", "11111", "01110", "00100"]  # +

# Barrel (thùng)
BARREL = ["01110", "10101", "10101", "10101", "01110"]  # barrel with lines

def make_action_card(pixels, symbol, color, label=None):
    """Add symbol to card, optionally label at top."""
    cx, cy = (CARD_W - 5) // 2, (CARD_H - 5) // 2 - 2
    draw_pattern(pixels, CARD_W, cx, cy, symbol, color)

def make_generic_card(bg_color=WHITE):
    pixels = new_canvas(CARD_W, CARD_H, bg_color)
    border(pixels, CARD_W, 0, 0, CARD_W, CARD_H, GRAY, 1)
    return pixels

def make_card_with_symbol(symbol, color, extra=None):
    """extra: (pattern, color, dx, dy) for additional small symbol"""
    pixels = make_generic_card()
    sw = len(symbol[0]) if symbol else 5
    sh = len(symbol) if symbol else 5
    cx, cy = (CARD_W - sw) // 2, (CARD_H - sh) // 2 - 2
    draw_pattern(pixels, CARD_W, cx, cy, symbol, color)
    if extra:
        pat, c, dx, dy = extra
        draw_pattern(pixels, CARD_W, cx + dx, cy + dy, pat, c)
    return pixels

def make_draw_card(n, color=BLACK):
    """+2, +3: plus + n card frames"""
    pixels = make_generic_card()
    # Big + center
    plus = ["00100", "01110", "11111", "01110", "00100"]
    cx, cy = (CARD_W - 5) // 2, 6
    draw_pattern(pixels, CARD_W, cx, cy, plus, color)
    # Card frames (small rectangles) below
    for i in range(n):
        fx, fy = 4 + i * 6, 18
        border(pixels, CARD_W, fx, fy, fx + 5, fy + 8, GRAY, 1)
    return pixels

def make_heal_card(n, color=GREEN):
    """Hồi n máu: n green +"""
    pixels = make_generic_card()
    plus = ["00100", "01110", "11111", "01110", "00100"]
    if n == 1:
        cx, cy = (CARD_W - 5) // 2, (CARD_H - 5) // 2 - 2
        draw_pattern(pixels, CARD_W, cx, cy, plus, color)
    else:
        for i in range(n):
            cx = (CARD_W - 5) // 2 + (i - 1) * 6
            cy = (CARD_H - 5) // 2 - 2
            draw_pattern(pixels, CARD_W, cx, cy, plus, color)
    return pixels

def make_gun_card(range_num):
    """Súng 1-5: bullet + số"""
    pixels = make_generic_card()
    cx, cy = (CARD_W - 5) // 2, 4
    draw_pattern(pixels, CARD_W, cx, cy, BANG, BLACK)
    # Number below
    num_pat = {
        1: ["010", "010", "010", "010", "010"],
        2: ["111", "001", "111", "100", "111"],
        3: ["111", "001", "111", "001", "111"],
        4: ["101", "101", "111", "001", "001"],
        5: ["111", "100", "111", "001", "111"],
    }
    draw_pattern(pixels, CARD_W, (CARD_W - 3) // 2, 18, num_pat.get(range_num, num_pat[1]), BLACK)
    return pixels

# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------
def main():
    print("Generating Bang! textures...")

    # 4 roles
    for role_id, pattern in ROLES.items():
        p = make_role_card(role_id, pattern)
        write_png(f"{BASE}/roles/{role_id}.png", p, CARD_W, CARD_H)

    # Action cards
    cards_dir = f"{BASE}/cards"
    cards = [
        ("bang", make_card_with_symbol(BANG, RED)),
        ("miss", make_card_with_symbol(MISS, GRAY)),
        ("beer", make_card_with_symbol(BEER, GREEN)),
        ("gatling", make_card_with_symbol(GATLING, RED)),
        ("saloon", make_card_with_symbol(SALOON, GREEN)),
        ("panico", make_card_with_symbol(PANICO_DOTS, BROWN)),
        ("cat_balou", make_card_with_symbol(CATBALOU, BROWN)),
        ("duello", make_card_with_symbol(DUELLO, RED)),
        ("indiani", make_card_with_symbol(INDIANI, BROWN)),
        ("dynamite", make_card_with_symbol(DYNAMITE, BROWN)),
        ("prison", make_card_with_symbol(PRISON, GRAY)),
        ("barrel", make_card_with_symbol(BARREL, BROWN)),
        ("mustang", make_card_with_symbol(MUSTANG, BROWN)),
        ("appaloosa", make_card_with_symbol(APPALOOSA, BROWN)),
    ]
    for name, p in cards:
        write_png(f"{cards_dir}/{name}.png", p, CARD_W, CARD_H)

    # Draw cards (+2, +3 rút từ chồng)
    for n in [2, 3]:
        p = make_draw_card(n)
        write_png(f"{cards_dir}/draw_{n}.png", p, CARD_W, CARD_H)

    # Heal cards (hồi máu - green +)
    for n in [1, 2]:
        p = make_heal_card(n, GREEN)
        write_png(f"{cards_dir}/heal_{n}.png", p, CARD_W, CARD_H)

    # Card back (mặt sau chồng bài Bang!)
    back = new_canvas(CARD_W, CARD_H, (40, 30, 20, 255))
    border(back, CARD_W, 0, 0, CARD_W, CARD_H, GOLD, 1)
    draw_pattern(back, CARD_W, (CARD_W - 5) // 2, (CARD_H - 5) // 2 - 2, BANG, GOLD)
    write_png(f"{BASE}/card_back.png", back, CARD_W, CARD_H)

    # Guns
    for r in range(1, 6):
        p = make_gun_card(r)
        write_png(f"{cards_dir}/gun_{r}.png", p, CARD_W, CARD_H)

    # General store
    p = make_draw_card(3)  # similar to draw 3
    write_png(f"{cards_dir}/general_store.png", p, CARD_W, CARD_H)

    print("\nDone! Bang! textures:")
    print(f"  {BASE}/roles/      - sheriff, outlaw, vice, renegade")
    print(f"  {BASE}/cards/     - bang, miss, beer, draw_2, draw_3, gun_1-5, ...")
    print(f"  {BASE}/card_back.png - deck back")

if __name__ == "__main__":
    main()
