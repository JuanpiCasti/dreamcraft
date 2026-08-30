from PIL import Image
import numpy as np
from scipy.ndimage import label, find_objects, binary_dilation
from pathlib import Path

im = Image.open(r'C:\Users\roman\Downloads\iconos2.png').convert('RGBA')
arr = np.array(im)
alpha = arr[:, :, 3] > 30

# Dilate by 10px so separated pixels belonging to one icon merge
dil = binary_dilation(alpha, iterations=10)
lbl, n = label(dil)
objs = find_objects(lbl)

print(f'Merged regions: {n}')
out_dir = Path('brand-data/iconos2_crops')
out_dir.mkdir(parents=True, exist_ok=True)

crops = []
for i, sl in enumerate(objs):
    sy, sx = sl
    w = sx.stop - sx.start
    h = sy.stop - sy.start
    if w >= 80 and h >= 80:
        c = im.crop((sx.start, sy.start, sx.stop, sy.stop))
        crops.append((sx.start, sy.start, w, h, c))

print(f'Found {len(crops)} major icons!')
for idx, (x, y, w, h, c) in enumerate(crops):
    c.save(out_dir / f'major_{idx:02d}_x{x}_y{y}_{w}x{h}.png')
    print(f'  #{idx:02d}: at ({x},{y}), size {w}x{h}')
