"""
PokerMC Texture Generator
Run: python generate_textures.py
Edit the output PNGs with any image editor (GIMP, Photoshop, Paint.NET).

Output files:
  src/main/resources/assets/pokermc/textures/gui/card_atlas.png   (286x128) - 52 cards sprite sheet
  src/main/resources/assets/pokermc/textures/gui/card_back.png    (22x32)   - card back
  src/main/resources/assets/pokermc/textures/gui/poker_table_bg.png (360x230) - game table GUI
  src/main/resources/assets/pokermc/textures/gui/lobby_bg.png     (260x180) - lobby/landing screen
  src/main/resources/assets/pokermc/textures/block/poker_table_top.png, poker_table_side.png - block
"""
import struct, zlib, os, math

# ---------------------------------------------------------------------------
# Raw PNG writer (no Pillow required)
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

def new_canvas(w, h, color=(0, 0, 0, 0)):
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

# ---------------------------------------------------------------------------
# Pixel fonts (3x5 for small, 5x7 for larger)
# ---------------------------------------------------------------------------
FONT_3x5 = {
    'A':['010','101','111','101','101'],
    '1':['010','010','010','010','010'],   # single vertical bar
    '2':['111','001','111','100','111'],
    '3':['111','001','111','001','111'],
    '4':['101','101','111','001','001'],
    '5':['111','100','111','001','111'],
    '6':['111','100','111','101','111'],
    '7':['111','001','001','001','001'],
    '8':['111','101','111','101','111'],
    '9':['111','101','111','001','111'],
    '0':['111','101','101','101','111'],
    'T':['111','010','010','010','010'],
    'J':['011','001','001','101','111'],
    'Q':['111','101','101','110','001'],
    'K':['101','110','100','110','101'],
    'Z':['111','001','010','100','111'],
    '!':['010','010','010','000','010'],
    # POKER text
    'P':['111','101','111','100','100'],
    'O':['111','101','101','101','111'],
    'E':['111','100','111','100','111'],
    'R':['111','101','111','110','101'],
}

def draw_char(pixels, W, cx, cy, ch, color):
    pat = FONT_3x5.get(ch.upper(), FONT_3x5.get('?', ['111']*5))
    for row, line in enumerate(pat):
        for col, bit in enumerate(line):
            if bit == '1':
                px(pixels, W, cx+col, cy+row, color)

def draw_text(pixels, W, x, y, text, color):
    cx = x
    for ch in text:
        draw_char(pixels, W, cx, y, ch, color)
        cx += 4

def draw_text_compact(pixels, W, x, y, text, color, spacing=3):
    """Draw text with smaller spacing for block textures."""
    cx = x
    for ch in text:
        draw_char(pixels, W, cx, y, ch, color)
        cx += spacing

# 5x5 suit symbols
SUIT_PIXELS = {
    'H': ['01010','11111','11111','01110','00100'],  # heart
    'D': ['00100','01110','11111','01110','00100'],  # diamond
    'S': ['00100','01110','11111','00100','01110'],  # spade
    'C': ['01110','01110','11111','00100','01110'],  # club
}

def draw_suit(pixels, W, cx, cy, suit, color):
    pat = SUIT_PIXELS.get(suit, ['00000']*5)
    for row, line in enumerate(pat):
        for col, bit in enumerate(line):
            if bit == '1':
                px(pixels, W, cx+col, cy+row, color)

# ---------------------------------------------------------------------------
# Palette
# ---------------------------------------------------------------------------
WHITE  = (255, 255, 255, 255)
BLACK  = (10, 10, 10, 255)
RED    = (200, 30, 30, 255)
GOLD   = (212, 175, 55, 255)
GOLD_L = (255, 223, 100, 255)
DARK   = (20, 20, 25, 255)
SILVER = (150, 150, 160, 255)
DARK_BG= (12, 12, 18, 255)
FELT   = (18, 80, 18, 255)
FELT_L = (25, 100, 25, 255)

BASE = "src/main/resources/assets/pokermc/textures"

# ---------------------------------------------------------------------------
# Card atlas  (286 x 128  = 13 cols x 4 rows, each card 22x32)
# Cleaner, lighter style: soft gray border, rank+suit in corners, single suit center
# ---------------------------------------------------------------------------
RANKS  = ['A','2','3','4','5','6','7','8','9','T','J','Q','K']
SUITS  = ['S','H','D','C']
SUIT_COLOR = {'S': (30, 30, 30, 255), 'H': (200, 30, 30, 255),
              'D': (200, 30, 30, 255), 'C': (30, 30, 30, 255)}

CARD_W, CARD_H = 22, 32

# ── 7×7 center suit symbols ───────────────────────────────────────────────────
#
#  ♥ Heart  — two bumps → taper → point (classic)
#  ♦ Diamond — perfect rotated square
#  ♠ Spade  — SOLID SHIELD: wide solid body + stem + wide feet (leaf/acorn shape)
#  ♣ Club   — THICK PLUS (+): horizontal bar × vertical bar  ← totally different!
#
SUIT_PIXELS_LG = {
    'H': ['0110110',
          '1111111',
          '1111111',
          '0111110',
          '0011100',
          '0001000',
          '0000000'],

    'D': ['0001000',
          '0011100',
          '0111110',
          '1111111',
          '0111110',
          '0011100',
          '0001000'],

    # ♠  Spade: single point → narrow → wide → wide belly → stem → feet
    'S': ['0001000',   # single sharp point at top
          '0011100',
          '0111110',
          '1111111',
          '1111111',   # solid wide belly
          '0001000',   # thin stem
          '0110110'],  # two feet

    # ♣  Club — theo design của user
    'C': ['0011100',
          '0011100',
          '1111111',
          '1111111',
          '0001000',
          '0011100',
          '0111110'],
}

# ── 3×3 corner suit symbols (used in lobby ornaments) ────────────────────────
SUIT_PIXELS_SM = {
    'H': ['010', '111', '010'],  # heart
    'D': ['010', '101', '010'],  # diamond (open center)
    'S': ['010', '111', '101'],  # spade: 1 top dot, wide, 2 bottom feet
    'C': ['101', '111', '010'],  # club: 2 top dots, wide, 1 bottom stem
}

def make_card_atlas():
    W, H = CARD_W * 13, CARD_H * 4
    pixels = new_canvas(W, H, (0, 0, 0, 0))

    LIGHT_GRAY = (200, 200, 200, 255)
    CARD_BG    = (252, 252, 252, 255)   # near-white, very light

    for si, suit in enumerate(SUITS):
        for ri, rank in enumerate(RANKS):
            ox = ri * CARD_W
            oy = si * CARD_H
            col = SUIT_COLOR[suit]

            # Card background (very light) + soft border
            fill(pixels, W, ox, oy, ox+CARD_W, oy+CARD_H, CARD_BG)
            border(pixels, W, ox, oy, ox+CARD_W, oy+CARD_H, LIGHT_GRAY, 1)

            # TOP-LEFT: rank (use "10" for T, else single char)
            if rank == 'T':
                draw_text(pixels, W, ox+2, oy+2, '10', col)
            else:
                draw_char(pixels, W, ox+2, oy+2, rank[0], col)

            # BOTTOM-RIGHT: same rank (right-align "10" so it doesn't overflow)
            if rank == 'T':
                draw_text(pixels, W, ox+CARD_W-9, oy+CARD_H-7, '10', col)
            else:
                draw_char(pixels, W, ox+CARD_W-5, oy+CARD_H-7, rank[0], col)

            # CENTER: 7×7 suit symbol
            pat_lg = SUIT_PIXELS_LG.get(suit, ['0000000']*7)
            sx = ox + (CARD_W - 7) // 2   # horizontally centered
            sy = oy + 13                   # vertically centered in card body
            for row, line in enumerate(pat_lg):
                for c, bit in enumerate(line):
                    if bit == '1':
                        px(pixels, W, sx+c, sy+row, col)

    write_png(f"{BASE}/gui/card_atlas.png", pixels, W, H)
    print("  Atlas: col=A..K, row=S/H/D/C")

# ---------------------------------------------------------------------------
# Card back  (22x32) — deep navy, multi-layer gold border, diamond lattice,
#                       LARGE 2× 'Z' prominently in center
# ---------------------------------------------------------------------------
def draw_char_2x(pixels, W, cx_pos, cy_pos, ch, color):
    """Draw a 3×5 font character at 2× scale (each pixel → 2×2 block = 6×10 px)."""
    pat = FONT_3x5.get(ch.upper(), ['000'] * 5)
    for row, line in enumerate(pat):
        for col, bit in enumerate(line):
            if bit == '1':
                fill(pixels, W,
                     cx_pos + col*2, cy_pos + row*2,
                     cx_pos + col*2+2, cy_pos + row*2+2,
                     color)

def make_card_back():
    W, H = CARD_W, CARD_H
    pixels = new_canvas(W, H)

    NAVY    = (8,  8,  35, 255)
    G       = GOLD
    GL      = GOLD_L
    GOLD_BR = (255, 245, 80, 255)

    # ── Base fill ─────────────────────────────────────────────────────────────
    fill(pixels, W, 0, 0, W, H, NAVY)

    # ── 2-layer gold border (outer + inner, clean gap between) ───────────────
    border(pixels, W, 0, 0, W, H,     G,  1)   # outer gold
    # 1px navy gap (already navy)
    border(pixels, W, 2, 2, W-2, H-2, GL, 1)   # inner gold (brighter)

    # ── 4 corner dots only — minimal ornament ─────────────────────────────────
    for (ocx, ocy) in [(3, 3), (W-4, 3), (3, H-4), (W-4, H-4)]:
        px(pixels, W, ocx, ocy, GL)

    # ── Large 'Z' centered — 2× scale (6×10 px) ──────────────────────────────
    zx = W//2 - 3   # left edge of Z: 8
    zy = H//2 - 5   # top edge of Z:  11
    draw_char_2x(pixels, W, zx, zy, 'Z', GOLD_BR)

    write_png(f"{BASE}/gui/card_back.png", pixels, W, H)

# ---------------------------------------------------------------------------
# Game table GUI background  (360x230) - dark elegant with gold details
# ---------------------------------------------------------------------------
def make_table_bg():
    W, H = 360, 230
    pixels = new_canvas(W, H)

    # Dark background
    fill(pixels, W, 0, 0, W, H, DARK_BG)

    # Silver/dark border
    border(pixels, W, 0, 0, W, H, GOLD, 3)
    border(pixels, W, 4, 4, W-4, H-4, (40, 40, 50, 255), 1)

    # Dark felt oval center
    cx, cy = W//2, H//2
    for y in range(H):
        for x in range(W):
            dx = (x - cx) / (W/2 - 20)
            dy = (y - cy) / (H/2 - 20)
            if dx*dx + dy*dy < 1.0:
                # Dark green felt inside oval
                dist = dx*dx + dy*dy
                intensity = int(20 + (1.0 - dist) * 25)
                fill(pixels, W, x, y, x+1, y+1, (0, intensity + 40, intensity, 255))

    # Gold oval outline
    for angle_deg in range(0, 360, 2):
        a = math.radians(angle_deg)
        ex = int(cx + math.cos(a) * (W/2 - 22))
        ey = int(cy + math.sin(a) * (H/2 - 22))
        if 0 <= ex < W and 0 <= ey < H:
            pixels[ey * W + ex] = GOLD

    # Corner gold ornaments (small diamond shapes)
    for ox, oy in [(10,10),(W-10,10),(10,H-10),(W-10,H-10)]:
        for d in [-2,-1,0,1,2]:
            px(pixels, W, ox, oy+d, GOLD_L)
            px(pixels, W, ox+d, oy, GOLD_L)

    write_png(f"{BASE}/gui/poker_table_bg.png", pixels, W, H)

# ---------------------------------------------------------------------------
# Lobby background  (260x180) - elegant dark with gold accents
# ---------------------------------------------------------------------------
def make_lobby_bg():
    W, H = 260, 180
    pixels = new_canvas(W, H)

    # Very dark background
    fill(pixels, W, 0, 0, W, H, (8, 8, 12, 255))

    # Multi-layer gold border
    border(pixels, W, 0, 0, W, H, GOLD, 2)
    border(pixels, W, 3, 3, W-3, H-3, (60, 50, 20, 255), 1)
    border(pixels, W, 5, 5, W-5, H-5, GOLD, 1)

    # Central diamond pattern
    cx, cy = W//2, H//2
    for i in range(0, min(W,H)//2 - 20, 15):
        for angle_deg in range(0, 360, 5):
            a = math.radians(angle_deg)
            ex = int(cx + math.cos(a) * i)
            ey = int(cy + math.sin(a) * i * 0.6)
            if 0 <= ex < W and 0 <= ey < H:
                pixels[ey * W + ex] = (30, 25, 10, 255)

    # Subtle suit symbols in corners (very dim gold)
    DIM_GOLD = (60, 50, 15, 255)
    for (ox, oy, suit) in [(15,15,'S'),(W-25,15,'H'),(15,H-22,'D'),(W-25,H-22,'C')]:
        draw_suit(pixels, W, ox, oy, suit, DIM_GOLD)

    # Title area highlight bar
    fill(pixels, W, 6, 30, W-6, 50, (20, 15, 5, 255))
    border(pixels, W, 6, 30, W-6, 50, GOLD, 1)

    write_png(f"{BASE}/gui/lobby_bg.png", pixels, W, H)

# ---------------------------------------------------------------------------
# Block texture  (16x16)
# ---------------------------------------------------------------------------
# ZCoin mini icon (5x5): round circle
# ---------------------------------------------------------------------------
ZCOIN_5x5 = [
    '01110',
    '10001',
    '10001',
    '10001',
    '01110',
]
def draw_zcoin_mini(pixels, W, cx, cy, color):
    """Draw small ZCoin circle at cx,cy."""
    for row, line in enumerate(ZCOIN_5x5):
        for col, bit in enumerate(line):
            if bit == '1':
                px(pixels, W, cx+col, cy+row, color)

# ---------------------------------------------------------------------------
# Poker table block: top = felt + POKER + ZCoin, side = wood
# ---------------------------------------------------------------------------
def make_block_texture():
    W = H = 16
    WOOD   = (60, 35, 10, 255)
    WOOD_D = (40, 22, 5, 255)
    FELT   = (18, 80, 18, 255)   # green felt
    FELT_D = (12, 55, 12, 255)  # darker felt edge

    # ---- Top: green felt with POKER text and ZCoin ----
    top = new_canvas(W, H)
    fill(top, W, 0, 0, W, H, FELT)
    border(top, W, 0, 0, W, H, GOLD, 1)
    fill(top, W, 1, 1, W-1, H-1, FELT)
    # POKER text centered - bolder, clearer (y=4 for better visibility)
    draw_text_compact(top, W, 0, 4, "POKER", GOLD_L, 3)
    # ZCoin icons on table (2 coins)
    draw_zcoin_mini(top, W, 2, 10, GOLD)
    draw_zcoin_mini(top, W, 9, 10, GOLD)
    # Z in center of each coin
    draw_char(top, W, 3, 11, 'Z', (180, 140, 40, 255))
    draw_char(top, W, 10, 11, 'Z', (180, 140, 40, 255))
    write_png(f"{BASE}/block/poker_table_top.png", top, W, H)

    # ---- Side: dark polished frame (not wood) ----
    FRAME   = (45, 45, 50, 255)   # dark charcoal
    FRAME_D = (30, 30, 35, 255)  # darker edge
    side = new_canvas(W, H)
    fill(side, W, 0, 0, W, H, FRAME)
    border(side, W, 0, 0, W, H, FRAME_D, 1)
    write_png(f"{BASE}/block/poker_table_side.png", side, W, H)

# ---------------------------------------------------------------------------
if __name__ == "__main__":
    print("Generating PokerMC textures...")
    make_card_atlas()
    make_card_back()
    make_table_bg()
    make_lobby_bg()
    make_block_texture()
    print("\nDone! Editable PNG files:")
    print(f"  {BASE}/gui/card_atlas.png   - 52 cards (13 cols x 4 rows)")
    print(f"  {BASE}/gui/card_back.png    - card back design")
    print(f"  {BASE}/gui/poker_table_bg.png - game table background")
    print(f"  {BASE}/gui/lobby_bg.png     - lobby/landing screen background")
    print(f"  {BASE}/block/poker_table_top.png  - block top (felt + POKER + ZCoin)")
    print(f"  {BASE}/block/poker_table_side.png - block side (wood)")
