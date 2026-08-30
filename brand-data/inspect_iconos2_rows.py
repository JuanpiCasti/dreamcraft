from PIL import Image
import numpy as np

im = Image.open('brand-data/prompts/iconos2.png').convert('RGBA')
arr = np.array(im)
alpha = arr[:, :, 3]

row_ranges = [(103, 443), (455, 793), (804, 1142)]
for r_idx, (y0, y1) in enumerate(row_ranges):
    row_alpha = alpha[y0:y1, :]
    # Project on X
    proj_x = np.count_nonzero(row_alpha > 30, axis=0)
    print(f'=== Row {r_idx} (y={y0}..{y1}) ===')
    in_col = False
    x_start = 0
    for x, val in enumerate(proj_x):
        if val > 10 and not in_col:
            in_col = True
            x_start = x
        elif val <= 10 and in_col:
            in_col = False
            print(f'  Col: x={x_start}..{x} (width {x - x_start})')
    if in_col:
        print(f'  Col: x={x_start}..{len(proj_x)} (width {len(proj_x) - x_start})')
