from PIL import Image
import numpy as np
from pathlib import Path

ROOT = Path('resource-packs/packs/dreamcraft/assets/dreamcraft/textures/item')
PROMPTS = Path('brand-data/prompts')

def crop_and_square_32(im, bbox):
    crop = im.crop(bbox)
    # Tight trim by alpha
    arr = np.array(crop)
    alpha = arr[:, :, 3] > 20
    if np.any(alpha):
        ys, xs = np.where(alpha)
        crop = crop.crop((xs.min(), ys.min(), xs.max() + 1, ys.max() + 1))
    w, h = crop.size
    side = max(w, h)
    sq = Image.new('RGBA', (side, side), (0, 0, 0, 0))
    sq.paste(crop, ((side - w) // 2, (side - h) // 2))
    return sq.resize((32, 32), Image.LANCZOS)

# 1. 06-iconos-city.png
im_city = Image.open(PROMPTS / '06-iconos-city.png').convert('RGBA')
crop_and_square_32(im_city, (253, 29, 697, 505)).save(ROOT / 'city' / 'treasury.png')
crop_and_square_32(im_city, (909, 50, 1320, 505)).save(ROOT / 'city' / 'admin.png')
crop_and_square_32(im_city, (249, 532, 705, 968)).save(ROOT / 'city' / 'icon.png')
crop_and_square_32(im_city, (906, 515, 1322, 981)).save(ROOT / 'city' / 'score.png')
print('City icons updated from 06-iconos-city.png')

# 2. 07-iconos-estate.png
im_estate = Image.open(PROMPTS / '07-iconos-estate.png').convert('RGBA')
crop_and_square_32(im_estate, (895, 8, 1322, 488)).save(ROOT / 'estate' / 'dragon.png')
crop_and_square_32(im_estate, (879, 505, 1354, 1006)).save(ROOT / 'estate' / 'adventure.png')
crop_and_square_32(im_estate, (210, 8, 680, 510)).save(ROOT / 'estate' / 'icon.png')
crop_and_square_32(im_estate, (210, 520, 680, 1006)).save(ROOT / 'estate' / 'zone-tp.png')
crop_and_square_32(im_estate, (210, 520, 680, 1006)).save(ROOT / 'estate' / 'instance.png')
print('Estate icons updated from 07-iconos-estate.png')

# 3. iconos2.png (the 12 cuts from C:\Users\roman\Downloads\iconos2.png)
im_iconos2 = Image.open(PROMPTS / 'iconos2.png').convert('RGBA')
xs = [160, 460, 750, 1040]
ys = [273, 624, 973]
# Row 0: Matriz (Azul)
# Row 1: Nexo (Violeta)
# Row 2: Sync (Dorado/Acero)
names = [
    # (row, col, filename)
    (0, 0, 'menu/profile_matriz.png'),
    (0, 1, 'menu/roles_matriz.png'),
    (0, 2, 'menu/invite_matriz.png'),
    (0, 3, 'menu/join_matriz.png'),
    (1, 0, 'menu/profile_nexo.png'),
    (1, 1, 'menu/roles_nexo.png'),
    (1, 2, 'menu/invite_nexo.png'),
    (1, 3, 'menu/join_nexo.png'),
    (2, 0, 'menu/profile_sync.png'),
    (2, 1, 'menu/roles_sync.png'),
    (2, 2, 'menu/invite_sync.png'),
    (2, 3, 'menu/join_sync.png'),
]
for r, c, rel in names:
    cx = xs[c]
    cy = ys[r]
    box = (cx - 140, cy - 140, cx + 140, cy + 140)
    out = crop_and_square_32(im_iconos2, box)
    dst = ROOT / rel
    dst.parent.mkdir(parents=True, exist_ok=True)
    out.save(dst)
    print(f'Saved {rel}')

# Assign primary base textures
crop_and_square_32(im_iconos2, (xs[0] - 140, ys[2] - 140, xs[0] + 140, ys[2] + 140)).save(ROOT / 'menu' / 'profile.png')
crop_and_square_32(im_iconos2, (xs[2] - 140, ys[0] - 140, xs[2] + 140, ys[0] + 140)).save(ROOT / 'menu' / 'invite.png')
crop_and_square_32(im_iconos2, (xs[1] - 140, ys[0] - 140, xs[1] + 140, ys[0] + 140)).save(ROOT / 'menu' / 'roles.png')
crop_and_square_32(im_iconos2, (xs[1] - 140, ys[0] - 140, xs[1] + 140, ys[0] + 140)).save(ROOT / 'menu' / 'members.png')

# 4. updatedsprites.png navigation/action icons
im_up = Image.open(PROMPTS / 'updatedsprites.png').convert('RGBA')
# #32 is close X blanca: cx=1099, cy=616
crop_and_square_32(im_up, (1099 - 75, 616 - 75, 1099 + 75, 616 + 75)).save(ROOT / 'menu' / 'close.png')
# #33 is kick X roja: cx=1279, cy=617
crop_and_square_32(im_up, (1279 - 75, 617 - 75, 1279 + 75, 617 + 75)).save(ROOT / 'menu' / 'kick.png')
# #04 is back arrow: cx=918, cy=98
crop_and_square_32(im_up, (918 - 75, 98 - 75, 918 + 75, 98 + 75)).save(ROOT / 'menu' / 'back.png')
# #06 is confirm check: cx=1280, cy=97
crop_and_square_32(im_up, (1280 - 75, 97 - 75, 1280 + 75, 97 + 75)).save(ROOT / 'menu' / 'confirm.png')
print('Action buttons (close, kick, back, confirm) updated from updatedsprites.png')

print('ALL ASSET UPDATES COMPLETED SUCCESSFULLY.')
