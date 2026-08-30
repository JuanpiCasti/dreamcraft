from PIL import Image, ImageDraw
import numpy as np
from scipy.ndimage import label, find_objects
from pathlib import Path

im = Image.open(r'C:\Users\roman\Downloads\iconos2.png').convert('RGBA')
arr = np.array(im)
alpha = arr[:, :, 3] > 20

lbl, n = label(alpha)
objs = find_objects(lbl)
print(f'Objects in iconos2.png: {n}')

out_dir = Path('brand-data/iconos2_crops')
out_dir.mkdir(parents=True, exist_ok=True)

crops = []
for i, sl in enumerate(objs):
    sy, sx = sl
    w = sx.stop - sx.start
    h = sy.stop - sy.start
    if w >= 30 and h >= 30:
        c = im.crop((sx.start, sy.start, sx.stop, sy.stop))
        # Center in square
        s = max(w, h) + 8
        sq = Image.new('RGBA', (s, s), (0, 0, 0, 0))
        sq.paste(c, ((s - w) // 2, (s - h) // 2))
        sq.save(out_dir / f'cut_{len(crops):02d}_w{w}h{h}_x{sx.start}_y{sy.start}.png')
        crops.append((sx.start, sy.start, sq))

# Sort top-to-bottom, left-to-right
crops.sort(key=lambda item: (item[1] // 80, item[0]))
print(f'Total meaningful crops: {len(crops)}')

# Make grid
grid = Image.new('RGBA', (6 * 140, 5 * 140), (20, 20, 25, 255))
draw = ImageDraw.Draw(grid)
for idx, (_, _, c) in enumerate(crops):
    r = idx // 6
    col = idx % 6
    thumb = c.copy()
    thumb.thumbnail((120, 120), Image.LANCZOS)
    tw, th = thumb.size
    grid.alpha_composite(thumb, (col * 140 + (140 - tw) // 2, r * 140 + (140 - th) // 2))
    draw.text((col * 140 + 5, r * 140 + 5), f'#{idx:02d}', fill=(255, 255, 0, 255))

grid.save('brand-data/iconos2_grid.png')
print('Saved brand-data/iconos2_grid.png')
