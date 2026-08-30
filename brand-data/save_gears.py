from PIL import Image
import numpy as np
from pathlib import Path

ROOT = Path('resource-packs/packs/dreamcraft/assets/dreamcraft/textures/item')
SRC = Path('brand-data/prompts/icons_extracted')

def crop_tight_to_32(crop_path):
    im = Image.open(crop_path).convert('RGBA')
    arr = np.array(im)
    alpha = arr[:, :, 3] > 30
    if not np.any(alpha):
        return im.resize((32, 32), Image.LANCZOS)
    ys, xs = np.where(alpha)
    tight = im.crop((xs.min(), ys.min(), xs.max() + 1, ys.max() + 1))
    w, h = tight.size
    side = max(w, h)
    sq = Image.new('RGBA', (side, side), (0, 0, 0, 0))
    sq.paste(tight, ((side - w) // 2, (side - h) // 2))
    return sq.resize((32, 32), Image.LANCZOS)

crops = sorted(list(SRC.glob('*.png')))
# #40 is gear sync
crop_tight_to_32(crops[40]).save(ROOT / 'menu' / 'gear_sync.png')
crop_tight_to_32(crops[40]).save(ROOT / 'menu' / 'gear.png')
crop_tight_to_32(crops[40]).save(ROOT / 'ward' / 'disband.png')
# #37 is gear matriz
crop_tight_to_32(crops[37]).save(ROOT / 'menu' / 'gear_matriz.png')
# #34 is gear nexo
crop_tight_to_32(crops[34]).save(ROOT / 'menu' / 'gear_nexo.png')

print('Gear icons saved successfully.')
