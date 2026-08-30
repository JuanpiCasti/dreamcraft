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
        if w >= 80 and h >= 80:
            boxes.append((sx.start, sy.start, sx.stop, sy.stop, w, h))
    return boxes

print('06-iconos-city.png boxes:')
for b in find_boxes('brand-data/prompts/06-iconos-city.png'):
    print(f'  bbox=({b[0]}, {b[1]}, {b[2]}, {b[3]}), size={b[4]}x{b[5]}')

print('07-iconos-estate.png boxes:')
for b in find_boxes('brand-data/prompts/07-iconos-estate.png'):
    print(f'  bbox=({b[0]}, {b[1]}, {b[2]}, {b[3]}), size={b[4]}x{b[5]}')
