from PIL import Image
from pathlib import Path

im = Image.open('brand-data/prompts/iconos2.png').convert('RGBA')
out_dir = Path('brand-data/prompts/iconos2_cuts')
out_dir.mkdir(exist_ok=True)

xs = [160, 460, 750, 1040]
ys = [273, 624, 973]

idx = 0
for r_idx, cy in enumerate(ys):
    for c_idx, cx in enumerate(xs):
        x0 = max(0, cx - 140)
        y0 = max(0, cy - 140)
        x1 = min(im.size[0], cx + 140)
        y1 = min(im.size[1], cy + 140)
        crop = im.crop((x0, y0, x1, y1))
        crop.save(out_dir / f'cut_{idx:02d}_r{r_idx}_c{c_idx}.png')
        idx += 1

print(f'Extracted 12 cuts to {out_dir}')
