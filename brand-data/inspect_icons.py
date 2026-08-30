from PIL import Image
import numpy as np
from pathlib import Path

crops = sorted(list(Path('brand-data/prompts/icons_extracted').glob('*.png')))
for c in crops:
    im = Image.open(c).convert('RGBA')
    arr = np.array(im)
    alpha = arr[:, :, 3]
    rgb = arr[:, :, :3]
    solid = alpha > 40
    
    # Check bounding box of actual drawing inside 160x160
    rows = np.any(solid, axis=1)
    cols = np.any(solid, axis=0)
    if not np.any(rows):
        print(f'{c.stem}: EMPTY')
        continue
    ymin, ymax = np.where(rows)[0][[0, -1]]
    xmin, xmax = np.where(cols)[0][[0, -1]]
    w = xmax - xmin + 1
    h = ymax - ymin + 1
    
    # Dominant colors
    pixels = rgb[solid]
    mean_c = pixels.mean(axis=0).astype(int)
    
    print(f'{c.stem:25s}: draw_size={w:3d}x{h:3d} at [{xmin:2d},{ymin:2d}], mean_rgb=[{mean_c[0]:3d},{mean_c[1]:3d},{mean_c[2]:3d}]')
