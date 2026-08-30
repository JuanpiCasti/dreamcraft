from PIL import Image, ImageDraw
import numpy as np
from pathlib import Path

im = Image.open('brand-data/prompts/updatedsprites.png').convert('RGBA')
arr = np.array(im)
alpha = arr[:, :, 3]

# Centers list
row03_xs = [110, 305, 490, 668, 918, 1099, 1280, 1485, 1663]
row_ys = [98, 276, 459, 645]

centers = []
for ry in row_ys:
    for rx in row03_xs:
        # Avoid non-existent spots in row 3 (col 7 and 8)
        if ry == 645 and rx in (1485, 1663):
            continue
        centers.append((rx, ry))

row4_xs = [337, 499, 659, 824, 977, 1128, 1272, 1415, 1560]
for rx in row4_xs:
    centers.append((rx, 794))

out_dir = Path('brand-data/prompts/icons_extracted')
out_dir.mkdir(exist_ok=True)

# For each center, find tight bbox of pixels with alpha > 25 in a 160x160 window
crops = []
for idx, (cx, cy) in enumerate(centers):
    x0 = max(0, cx - 85)
    y0 = max(0, cy - 85)
    x1 = min(im.size[0], cx + 85)
    y1 = min(im.size[1], cy + 85)
    
    sub = arr[y0:y1, x0:x1]
    sub_a = sub[:, :, 3] > 25
    if np.any(sub_a):
        ys, xs = np.where(sub_a)
        # Tight coordinates relative to sub
        real_x0 = x0 + xs.min()
        real_x1 = x0 + xs.max() + 1
        real_y0 = y0 + ys.min()
        real_y1 = y0 + ys.max() + 1
    else:
        real_x0, real_x1, real_y0, real_y1 = x0, x1, y0, y1
        
    w = real_x1 - real_x0
    h = real_y1 - real_y0
    side = max(w, h) + 4
    tight_crop = im.crop((real_x0, real_y0, real_x1, real_y1))
    
    sq = Image.new('RGBA', (side, side), (0, 0, 0, 0))
    sq.paste(tight_crop, ((side - w) // 2, (side - h) // 2))
    
    save_path = out_dir / f'icon_{idx:02d}_x{cx}_y{cy}.png'
    sq.save(save_path)
    crops.append(sq)

print(f'Successfully re-extracted {len(crops)} perfectly centered icons directly from updatedsprites.png.')

# Generate new icons_grid.png
cols = 7
rows = (len(crops) + cols - 1) // cols
grid_w = cols * 140
grid_h = rows * 140
grid = Image.new('RGBA', (grid_w, grid_h), (18, 18, 24, 255))
draw = ImageDraw.Draw(grid)

for idx, c in enumerate(crops):
    r = idx // cols
    col = idx % cols
    # fit in 120x120
    thumb = c.copy()
    thumb.thumbnail((120, 120), Image.LANCZOS)
    tw, th = thumb.size
    grid.alpha_composite(thumb, (col * 140 + (140 - tw) // 2, r * 140 + (140 - th) // 2))
    draw.rectangle([col * 140, r * 140, (col + 1) * 140 - 1, (r + 1) * 140 - 1], outline=(50, 50, 65, 255))
    draw.text((col * 140 + 6, r * 140 + 6), f'#{idx:02d}', fill=(255, 230, 80, 255))

grid.save('brand-data/prompts/icons_grid.png')
print('brand-data/prompts/icons_grid.png successfully recreated with pristine crops.')
