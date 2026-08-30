from PIL import Image
import numpy as np
from pathlib import Path

crops = sorted(list(Path('brand-data/prompts/icons_extracted').glob('*.png')))
for idx in [0, 1, 7, 8, 16, 17, 25, 26, 32, 33]:
    c = crops[idx]
    im = Image.open(c).convert('RGBA')
    arr = np.array(im)
    solid = arr[:,:,3] > 60
    # Downsample to 24x24
    im24 = Image.fromarray((solid * 255).astype(np.uint8)).resize((24, 24), Image.BILINEAR)
    arr24 = np.array(im24) > 80
    print(f'=== Symbol #{idx:02d} ({c.name}) ===')
    for row in arr24:
        print(''.join(['#' if p else ' ' for p in row]))
