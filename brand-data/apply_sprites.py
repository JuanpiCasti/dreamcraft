from PIL import Image
from pathlib import Path
import shutil

ROOT = Path('resource-packs/packs/dreamcraft/assets/dreamcraft/textures/item')
SRC = Path('brand-data/prompts/icons_extracted')

def crop_tight_to_32(crop_path):
    im = Image.open(crop_path).convert('RGBA')
    # find alpha bounding box
    import numpy as np
    arr = np.array(im)
    alpha = arr[:, :, 3] > 30
    if not np.any(alpha):
        return im.resize((32, 32), Image.LANCZOS)
    ys, xs = np.where(alpha)
    tight = im.crop((xs.min(), ys.min(), xs.max() + 1, ys.max() + 1))
    w, h = tight.size
    side = max(w, h)
    # square transparent canvas
    sq = Image.new('RGBA', (side, side), (0, 0, 0, 0))
    sq.paste(tight, ((side - w) // 2, (side - h) // 2))
    return sq.resize((32, 32), Image.LANCZOS)

crops = sorted(list(SRC.glob('*.png')))
mapping = {
    # Generic menu icons
    'menu/close.png': crops[32],
    'menu/kick.png': crops[33],
    'menu/back.png': crops[4],
    'menu/confirm.png': crops[6],
    'menu/profile.png': crops[2],
    'menu/roles.png': crops[3],
    'menu/members.png': crops[3],
    
    # Ward / Sync icons
    'ward/icon.png': crops[25],
    'ward/tier.png': crops[26],
    'ward/inactive.png': crops[30],
    'nucleus/icon.png': crops[40],
    
    # City / Matriz icons
    'city/icon.png': crops[16],
    'city/score.png': crops[17],
    'city/admin.png': crops[37],
    
    # Estate / Nexo icons
    'estate/icon.png': crops[7],
    'estate/dragon.png': crops[8],
    'estate/zone-tp.png': crops[34],
    'estate/adventure.png': crops[34],
    'estate/instance.png': crops[34],
}

for rel_path, src_crop in mapping.items():
    dst = ROOT / rel_path
    dst.parent.mkdir(parents=True, exist_ok=True)
    res = crop_tight_to_32(src_crop)
    res.save(dst)
    print(f'Updated {rel_path} <- {src_crop.name}')

print('All texture files updated successfully.')
