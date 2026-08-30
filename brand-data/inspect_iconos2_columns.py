from PIL import Image
import numpy as np

im = Image.open(r'C:\Users\roman\Downloads\iconos2.png').convert('RGBA')
arr = np.array(im)
alpha = arr[:, :, 3]

y_rows = [(108, 439), (459, 789), (808, 1138)]

for r_idx, (y0, y1) in enumerate(y_rows):
    sub_a = alpha[y0:y1, :]
    x_prof = np.sum(sub_a > 20, axis=0)
    
    in_block = False
    x_ranges = []
    for x in range(im.size[0]):
        if x_prof[x] > 30 and not in_block:
            start_x = x
            in_block = True
        elif x_prof[x] <= 30 and in_block:
            x_ranges.append((start_x, x))
            in_block = False
    if in_block:
        x_ranges.append((start_x, im.size[0]))
    print(f'Row {r_idx} X-ranges ({len(x_ranges)} icons):', x_ranges)
