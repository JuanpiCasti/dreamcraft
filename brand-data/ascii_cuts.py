from PIL import Image
import numpy as np
from pathlib import Path

cuts = sorted(list(Path('brand-data/prompts/iconos2_cuts').glob('*.png')))
for idx, c in enumerate(cuts):
    im = Image.open(c).convert('RGBA')
    arr = np.array(im)
    alpha = arr[:, :, 3] > 50
    rgb = arr[:, :, :3]
    # bright foreground
    bright = alpha & ((rgb[:,:,0]>80) | (rgb[:,:,1]>80) | (rgb[:,:,2]>80))
    im20 = Image.fromarray((bright * 255).astype(np.uint8)).resize((20, 20), Image.BILINEAR)
    arr20 = np.array(im20) > 60
    
    # mean color of bright pixels
    mc = rgb[bright].mean(axis=0).astype(int) if np.any(bright) else [0,0,0]
    print(f'=== Cut #{idx:02d} ({c.stem}) mean_rgb={mc} ===')
    for row in arr20:
        print(''.join(['#' if p else ' ' for p in row]))
