from PIL import Image
import numpy as np
from pathlib import Path

crops = sorted(list(Path('brand-data/prompts/icons_extracted').glob('*.png')))
for idx in range(34, 43):
    c = crops[idx]
    im = Image.open(c).convert('RGBA')
    arr = np.array(im)
    # look at the foreground drawing (alpha > 80 and not dark background)
    rgb = arr[:,:,:3]
    bright = (arr[:,:,3] > 60) & ((rgb[:,:,0]>100) | (rgb[:,:,1]>100) | (rgb[:,:,2]>100))
    im24 = Image.fromarray((bright * 255).astype(np.uint8)).resize((24, 24), Image.BILINEAR)
    arr24 = np.array(im24) > 60
    print(f'=== Symbol #{idx:02d} ({c.name}) ===')
    for row in arr24:
        print(''.join(['#' if p else ' ' for p in row]))
