from PIL import Image
import numpy as np
from pathlib import Path

# Let's inspect the shapes of the non-connected or individual sprites
p = Path('brand-data/prompts/sprites_extracted')
for f in sorted(list(p.glob('*.png'))):
    im = Image.open(f).convert('RGBA')
    arr = np.array(im)
    alpha = arr[:, :, 3] > 40
    rgb = arr[:, :, :3]
    
    # Check if there is gold, red, blue, green, white, or distinct symbols
    w, h = im.size
    total_solid = np.count_nonzero(alpha)
    if total_solid == 0: continue
    
    # Colors
    is_white = np.count_nonzero((rgb[:,:,0]>200) & (rgb[:,:,1]>200) & (rgb[:,:,2]>200) & alpha)
    is_red = np.count_nonzero((rgb[:,:,0]>160) & (rgb[:,:,1]<80) & (rgb[:,:,2]<80) & alpha)
    is_gold = np.count_nonzero((rgb[:,:,0]>180) & (rgb[:,:,1]>130) & (rgb[:,:,2]<80) & alpha)
    is_cyan = np.count_nonzero((rgb[:,:,2]>160) & (rgb[:,:,1]>160) & (rgb[:,:,0]<100) & alpha)
    is_purple = np.count_nonzero((rgb[:,:,0]>120) & (rgb[:,:,2]>160) & (rgb[:,:,1]<100) & alpha)
    
    print(f'{f.stem:30s} {w:3d}x{h:3d} | Wht:{is_white:4d} Red:{is_red:4d} Gold:{is_gold:4d} Cyan:{is_cyan:4d} Purp:{is_purple:4d}')
