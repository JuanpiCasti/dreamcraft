from PIL import Image
import numpy as np
from pathlib import Path
import glob

for i in range(30, 43):
    files = glob.glob(f'brand-data/prompts/icons_extracted/icon_{i:02d}_*.png')
    if files:
        im = Image.open(files[0]).convert('RGBA')
        arr = np.array(im)
        a = arr[:, :, 3] > 20
        if np.any(a):
            mean_r = arr[:,:,0][a].mean()
            mean_g = arr[:,:,1][a].mean()
            mean_b = arr[:,:,2][a].mean()
            print(f'Icon #{i:02d}: size={im.size}, mean RGB=({mean_r:.0f}, {mean_g:.0f}, {mean_b:.0f}) -> {Path(files[0]).name}')
