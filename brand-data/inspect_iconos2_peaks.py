from PIL import Image
import numpy as np
from scipy.ndimage import distance_transform_edt, maximum_filter

im = Image.open('brand-data/prompts/iconos2.png').convert('RGBA')
arr = np.array(im)
alpha = arr[:, :, 3] > 100

# Distance transform on each row
row_ranges = [(103, 443), (455, 793), (804, 1142)]
for r_idx, (y0, y1) in enumerate(row_ranges):
    r_arr = arr[y0:y1, :]
    r_alpha = r_arr[:, :, 3] > 100
    dist = distance_transform_edt(r_alpha)
    local_max = maximum_filter(dist, size=50) == dist
    peaks = np.argwhere(local_max & (dist > 30))
    print(f'Row {r_idx}: {len(peaks)} local peaks')
    # group by x
    xs = sorted(list(set([round(p[1], -1) for p in peaks])))
    print(f'  X clusters: {xs}')
