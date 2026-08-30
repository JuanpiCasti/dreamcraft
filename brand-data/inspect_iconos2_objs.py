from PIL import Image
import numpy as np
from scipy.ndimage import label, find_objects

im = Image.open(r'C:\Users\roman\Downloads\iconos2.png').convert('RGBA')
arr = np.array(im)
alpha = arr[:, :, 3] > 0
lbl, n = label(alpha)
objs = find_objects(lbl)
print('Total objects with alpha > 0:', n)
for i, sl in enumerate(objs):
    sy, sx = sl
    print(f'  Obj {i}: x={sx.start}..{sx.stop} (w={sx.stop-sx.start}), y={sy.start}..{sy.stop} (h={sy.stop-sy.start})')
