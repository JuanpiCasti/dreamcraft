from PIL import Image
import numpy as np
from pathlib import Path

crops = sorted(list(Path('brand-data/prompts/icons_extracted').glob('*.png')))
# Let's inspect the shapes by looking at the inner pixels of each crop:
for idx, c in enumerate(crops):
    im = Image.open(c).convert('RGBA')
    arr = np.array(im)
    alpha = arr[:, :, 3] > 50
    rgb = arr[:, :, :3]
    # Check if there is an icon inside (central 80x80):
    inner = alpha[40:120, 40:120]
    inner_rgb = rgb[40:120, 40:120]
    solid_ratio = np.mean(inner)
    
    # Check edges:
    top_edge = np.mean(alpha[:20, :])
    bot_edge = np.mean(alpha[-20:, :])
    
    print(f'{c.stem}: solid_ratio={solid_ratio:.2f}, top={top_edge:.2f}, bot={bot_edge:.2f}')
