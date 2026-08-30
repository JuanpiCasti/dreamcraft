from PIL import Image
import numpy as np

im = Image.open('brand-data/prompts/10-bloque-synt.png').convert('RGBA')
print('10-bloque-synt size:', im.size)
arr = np.array(im)
print('Alpha min/max/mean:', arr[:,:,3].min(), arr[:,:,3].max(), arr[:,:,3].mean())

# Find the bounding boxes of the active (left) and inactive (right) faces
from scipy.ndimage import label, find_objects
alpha = arr[:,:,3] > 30
lbl, n = label(alpha)
objs = find_objects(lbl)
print(f'Found {n} objects in 10-bloque-synt')
for i, sl in enumerate(objs):
    sy, sx = sl
    w = sx.stop - sx.start
    h = sy.stop - sy.start
    if w >= 50 and h >= 50:
        print(f'  #{i}: box=({sx.start}, {sy.start}, {sx.stop}, {sy.stop}), size={w}x{h}')
