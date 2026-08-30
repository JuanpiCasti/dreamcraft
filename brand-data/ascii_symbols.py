from PIL import Image
import numpy as np
from pathlib import Path

crops = sorted(list(Path('brand-data/prompts/icons_extracted').glob('*.png')))
for idx in [2, 3, 4, 5, 6]:
    c = crops[idx]
    im = Image.open(c).convert('RGBA')
    arr = np.array(im)
    wht = (arr[:,:,0]>180) & (arr[:,:,1]>180) & (arr[:,:,2]>180)
    # Downsample to 24x24
    wht_im = Image.fromarray((wht * 255).astype(np.uint8)).resize((24, 24), Image.BILINEAR)
    wht_arr = np.array(wht_im) > 80
    print(f'=== Symbol #{idx:02d} ({c.name}) ===')
    for row in wht_arr:
        print(''.join(['#' if p else ' ' for p in row]))
