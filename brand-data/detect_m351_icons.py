from PIL import Image
import numpy as np
from scipy.ndimage import label, find_objects

im = Image.open('brand-data/prompts/menu_351_left.png').convert('RGBA')
arr = np.array(im)
alpha = arr[:,:,3] > 100
lbl, n = label(alpha)
objs = find_objects(lbl)
print(f'Found {n} objects in menu_351_left')
for i, sl in enumerate(objs):
    sy, sx = sl
    w = sx.stop - sx.start
    h = sy.stop - sy.start
    if 15 <= w <= 120 and 15 <= h <= 120:
        cx = (sx.start + sx.stop) // 2
        cy = (sy.start + sy.stop) // 2
        print(f'  Object at cx={cx}, cy={cy}, size={w}x{h}')
