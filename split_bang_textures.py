"""
Tach atlas Bang! thanh tung texture rieng.
- 1 la: copy truc tiep vao split/typeId_rankSuit.png
- Nhieu la: tach atlas roi luu vao split/
"""
import shutil
from pathlib import Path

try:
    from PIL import Image
except ImportError:
    print("Can cai Pillow: pip install Pillow")
    exit(1)

BASE = Path("src/main/resources/assets/bang/textures")
CARDS_DIR = BASE / "cards"
CARD_W, CARD_H = 22, 32

# typeId -> (possible filenames, layout G:cols, rankSuits order)
ATLAS_SPEC = {
    "bang": (["bang.png"], "G:5", ["AS","AH","KH","QH","AD","2D","3D","4D","5D","6D","7D","8D","9D","TD","JD","QD","KD","2C","3C","4C","5C","6C","7C","8C","9C"]),
    "missed": (["missed.png"], "G:3", ["2S","3S","4S","5S","6S","7S","8S","10C","JC","QC","KC","AC"]),
    "beer": (["beer.png"], "G:3", ["6H","7H","8H","9H","TH","JH"]),
    "barrel": (["barrel.png"], "G:2", ["QH","KH"]),
    "panic": (["panic.png"], "G:4", ["JH","QH","AH","8D"]),
    "cat_balou": (["cat_balou.png", "catbalou.png"], "G:4", ["9D","TD","JD","KH"]),
    "stagecoach": (["stagecoach.png", "Stagecoach.png"], "G:2", ["9S","9C"]),
    "indians": (["indians.png"], "G:2", ["KD","AD"]),
    "duel": (["duel.png"], "G:3", ["JS","8C","QD"]),
    "general_store": (["general_store.png", "generalstore.png", "General_Store.png"], "G:2", ["QS","JS"]),
    "schofield": (["schofield.png", "Schofield.png"], "G:3", ["QS","KC","JS"]),
    "volcanic": (["volcanic.png", "vocalnic.png", "Vocalnic.png"], "G:2", ["10C","10S"]),
    "mustang": (["mustang.png"], "G:2", ["8H","9H"]),
    "jail": (["jail.png"], "G:3", ["JS","10S","4H"]),
    "wells_fargo": (["wells_fargo.png", "Wells_Fargo.png"], "G:1", ["3H"]),
    "gatling": (["gatling.png"], "G:1", ["10H"]),
    "saloon": (["saloon.png", "Saloon.png"], "G:1", ["5H"]),
    "remington": (["remington.png"], "G:1", ["JS"]),
    "rev_carbine": (["rev_carbine.png", "rev.carbine.png", "Rev.Carbine.png"], "G:1", ["AD"]),
    "winchester": (["winchester.png", "Winchester.png"], "G:1", ["8S"]),
    "appaloosa": (["appaloosa.png"], "G:1", ["AC"]),
    "dynamite": (["dynamite.png"], "G:1", ["2S"]),
}

ROLE_SPEC = (["roles/role.png", "cards/Role.png"], "G:4", ["outlaw", "deputy", "renegade", "sheriff"])

OUTPUT_DIR = BASE / "cards" / "split"
ROLE_OUTPUT_DIR = BASE / "roles" / "split"


def find_file(candidates: list, base: Path) -> Path | None:
    """Tim file (case-insensitive) trong thu muc."""
    for c in candidates:
        p = base / c
        if p.exists():
            return p
    for f in base.iterdir():
        if f.is_file():
            for c in candidates:
                if f.name.lower() == Path(c).name.lower():
                    return f
    return None


def copy_single(type_id: str, src: Path, rank_suit: str) -> int:
    """Copy 1 la (22x32) vao split."""
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    dst = OUTPUT_DIR / f"{type_id}_{rank_suit.lower()}.png"
    img = Image.open(src).convert("RGBA")
    w, h = img.size
    if w == CARD_W and h == CARD_H:
        shutil.copy2(src, dst)
    else:
        img = img.resize((CARD_W, CARD_H), Image.Resampling.NEAREST)
        img.save(dst)
    print(f"  {type_id}_{rank_suit.lower()}.png (1 la)")
    return 1


def split_atlas(type_id: str, atlas_path: Path, layout: str, rank_suits: list) -> int:
    """Tach atlas, luu tung o thanh typeId_rankSuit.png."""
    img = Image.open(atlas_path).convert("RGBA")
    w, h = img.size

    cols = int(layout.split(":")[1])
    rows = (len(rank_suits) + cols - 1) // cols

    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    count = 0
    for i, rs in enumerate(rank_suits):
        col, row = i % cols, i // cols
        x, y = col * CARD_W, row * CARD_H
        if x + CARD_W <= w and y + CARD_H <= h:
            cell = img.crop((x, y, x + CARD_W, y + CARD_H))
            out_name = f"{type_id}_{rs.lower()}.png"
            cell.save(OUTPUT_DIR / out_name)
            print(f"  {out_name}")
            count += 1
    return count


def split_role(candidates: list, layout: str, names: list) -> int:
    """Tach role atlas."""
    atlas_path = find_file(["role.png", "Role.png"], BASE / "roles")
    if not atlas_path:
        atlas_path = find_file(["Role.png", "role.png"], BASE / "cards")
    if not atlas_path:
        print("  [SKIP] role.png not found")
        return 0

    img = Image.open(atlas_path).convert("RGBA")
    cols = int(layout.split(":")[1])
    ROLE_OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    count = 0
    for i, name in enumerate(names):
        col, row = i % cols, i // cols
        x, y = col * CARD_W, row * CARD_H
        cell = img.crop((x, y, x + CARD_W, y + CARD_H))
        cell.save(ROLE_OUTPUT_DIR / f"role_{name}.png")
        print(f"  role_{name}.png")
        count += 1
    return count


def main():
    print("Splitting Bang! textures...")
    print(f"Output: {OUTPUT_DIR}")
    total = 0

    for type_id, (filenames, layout, rank_suits) in ATLAS_SPEC.items():
        atlas_path = find_file(filenames, CARDS_DIR)
        print(f"\n[{type_id}] {layout} {len(rank_suits)} la")
        if not atlas_path:
            print(f"  [SKIP] not found")
            continue

        if layout == "G:1":
            total += copy_single(type_id, atlas_path, rank_suits[0])
        else:
            total += split_atlas(type_id, atlas_path, layout, rank_suits)

    print(f"\n[role] {ROLE_SPEC[1]}")
    total += split_role(ROLE_SPEC[0], ROLE_SPEC[1], ROLE_SPEC[2])

    print(f"\nTotal: {total} files.")


if __name__ == "__main__":
    main()
