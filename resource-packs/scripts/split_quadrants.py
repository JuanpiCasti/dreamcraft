from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
TEX = ROOT / "resource-packs" / "packs" / "dreamcraft" / "assets" / "dreamcraft" / "textures" / "item"
Q = TEX / "menu" / "q"

BASES = {
    "upkeep": "ward/upkeep.png",
    "fase": "ward/tier.png",
    "matriz": "city/icon.png",
    "tesoro": "city/treasury.png",
    "invite": "menu/invite.png",
    "roles": "menu/roles.png",
    "iniciar": "estate/icon.png",
    "salir": "menu/back.png",
    "ward": "ward/icon.png",
    "inactive": "ward/inactive.png",
    "zone-tp": "estate/zone-tp.png",
}
QUADS = {"tl": (0, 0), "tr": (16, 0), "bl": (0, 16), "br": (16, 16)}


def main() -> None:
    for group, rel in BASES.items():
        art = Image.open(TEX / rel).convert("RGBA")
        if art.size != (32, 32):
            raise SystemExit(f"{rel} is {art.size}, expected 32x32")
        for quad, (x, y) in QUADS.items():
            art.crop((x, y, x + 16, y + 16)).save(Q / f"{group}_{quad}.png")
    print(f"regenerated {len(BASES) * 4} quadrants from transparent base arts")


if __name__ == "__main__":
    main()
