from PIL import Image
import numpy as np
from scipy.ndimage import label, find_objects
from pathlib import Path

im = Image.open('brand-data/prompts/updatedsprites.png').convert('RGBA')
arr = np.array(im)
alpha = arr[:, :, 3] > 20

lbl, n = label(alpha)
objs = find_objects(lbl)

print(f'Total labeled regions: {n}')

# Extract bounding boxes of meaningful objects (>= 15x15)
clean_dir = Path('brand-data/prompts/updatedsprites_clean_crops')
clean_dir.mkdir(exist_ok=True)

crops_info = []
for i, sl in enumerate(objs):
    sy, sx = sl
    w = sx.stop - sx.start
    h = sy.stop - sy.start
    if w >= 15 and h >= 15:
        # Pad to square with 4px margin
        crop = im.crop((sx.start, sy.start, sx.stop, sy.stop))
        # Ensure symmetric square
        side = max(w, h) + 8
        sq = Image.new('RGBA', (side, side), (0, 0, 0, 0))
        sq.paste(crop, ((side - w) // 2, (side - h) // 2))
        sq.save(clean_dir / f'crop_{i:02d}_x{sx.start}_y{sy.start}_{w}x{h}.png')
        crops_info.append((i, sx.start, sy.start, w, h, clean_dir / f'crop_{i:02d}_x{sx.start}_y{sy.start}_{w}x{h}.png'))

print(f'Saved {len(crops_info)} clean crops to {clean_dir}')
