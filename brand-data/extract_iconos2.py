from PIL import Image
import shutil
from pathlib import Path
from scipy.ndimage import label, find_objects
import numpy as np

src = Path(r'C:\Users\roman\Downloads\iconos2.png')
dst = Path('brand-data/prompts/iconos2.png')
shutil.copy2(src, dst)
print(f'Copied {src} to {dst}')

im = Image.open(dst).convert('RGBA')
arr = np.array(im)
alpha = arr[:, :, 3] > 30

lbl, n = label(alpha)
objs = find_objects(lbl)
print(f'Found {n} objects in iconos2.png')

crops_dir = Path('brand-data/prompts/iconos2_extracted')
crops_dir.mkdir(exist_ok=True)

valid_crops = []
for i, sl in enumerate(objs):
    sy, sx = sl
    w = sx.stop - sx.start
    h = sy.stop - sy.start
    if w >= 20 and h >= 20:
        crop = im.crop((sx.start, sy.start, sx.stop, sy.stop))
        crop.save(crops_dir / f'crop_{i:02d}_{sx.start}_{sy.start}_{w}x{h}.png')
        valid_crops.append((i, sx.start, sy.start, w, h))

print(f'Saved {len(valid_crops)} valid crops (>=20x20) to {crops_dir}')
for c in valid_crops:
    print(f'  #{c[0]:02d}: x={c[1]}, y={c[2]}, size={c[3]}x{c[4]}')
