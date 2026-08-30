from PIL import Image
import numpy as np
from scipy.ndimage import label, find_objects

def find_boxes(path):
    im = Image.open(path).convert('RGBA')
    arr = np.array(im)
    alpha = arr[:, :, 3] > 30
    lbl, n = label(alpha)
    objs = find_objects(lbl)
    boxes = []
    for sl in objs:
        sy, sx = sl
        w = sx.stop - sx.start
        h = sy.stop - sy.start
        if w >= 40 and h >= 40:
            boxes.append((sx.start, sy.start, sx.stop, sy.stop, w, h))
    return boxes

print('05-iconos-ward.png boxes:')
for b in find_boxes('brand-data/prompts/05-iconos-ward.png'):
    print(f'  bbox=({b[0]}, {b[1]}, {b[2]}, {b[3]}), size={b[4]}x{b[5]}')
