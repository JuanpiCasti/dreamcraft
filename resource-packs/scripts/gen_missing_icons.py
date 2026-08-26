import numpy as np
from PIL import Image, ImageDraw

ROOT = Path = __import__("pathlib").Path(__file__).resolve().parents[2]
PROMPTS = ROOT / "brand-data" / "prompts"
OUT = ROOT / "resource-packs" / "packs" / "dreamcraft" / "assets" / "dreamcraft" / "textures" / "item"


def crop_icon(sheet: Image.Image, bbox: tuple) -> Image.Image:
    x0, y0, x1, y1 = bbox
    im = sheet.crop((x0, y0, x1 + 1, y1 + 1))
    w, h = im.size
    side = max(w, h)
    pad = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    pad.paste(im, ((side - w) // 2, (side - h) // 2), im)
    return pad.resize((32, 32), Image.LANCZOS)


def barrier_placeholder() -> Image.Image:
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    main = (224, 74, 224, 255)
    dark = (122, 31, 143, 255)
    light = (255, 170, 255, 255)
    cx = cy = 7.5
    for y in range(16):
        for x in range(16):
            dist = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            if 5.2 <= dist <= 7.4:
                d.point((x, y), fill=main)
    d.line([(3, 3), (12, 12)], fill=main, width=2)
    for i in range(4, 12):
        d.point((i, i), fill=light)
        d.point((i - 1, i), fill=dark)
        d.point((i, i - 1), fill=dark)
    for y in range(16):
        for x in range(16):
            if im.getpixel((x, y))[3] == 0:
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < 16 and 0 <= ny < 16 and im.getpixel((nx, ny))[3] > 0:
                        d.point((x, y), fill=dark)
                        break
    return im.resize((32, 32), Image.NEAREST)


def main() -> None:
    city = Image.open(PROMPTS / "06-iconos-city.png").convert("RGBA")
    estate = Image.open(PROMPTS / "07-iconos-estate.png").convert("RGBA")
    targets = [
        (crop_icon(city, (909, 50, 1319, 502)), OUT / "city" / "admin.png"),
        (crop_icon(estate, (238, 524, 668, 995)), OUT / "estate" / "instance.png"),
        (barrier_placeholder(), OUT / "ward" / "orphan.png"),
    ]
    for im, path in targets:
        path.parent.mkdir(parents=True, exist_ok=True)
        im.save(path)
        print(path, im.size)


if __name__ == "__main__":
    main()
