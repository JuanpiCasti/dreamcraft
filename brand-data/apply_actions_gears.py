from PIL import Image
from pathlib import Path

ROOT = Path('resource-packs/packs/dreamcraft/assets/dreamcraft/textures/item')
SRC = Path('brand-data/prompts/icons_extracted')
crops = sorted(list(SRC.glob('*.png')))

def to_32(im_path):
    im = Image.open(im_path).convert('RGBA')
    return im.resize((32, 32), Image.LANCZOS)

to_32(crops[32]).save(ROOT / 'menu' / 'close.png')
to_32(crops[33]).save(ROOT / 'menu' / 'kick.png')
to_32(crops[4]).save(ROOT / 'menu' / 'back.png')
to_32(crops[6]).save(ROOT / 'menu' / 'confirm.png')
to_32(crops[40]).save(ROOT / 'menu' / 'gear_sync.png')
to_32(crops[40]).save(ROOT / 'menu' / 'gear.png')
to_32(crops[40]).save(ROOT / 'ward' / 'disband.png')
to_32(crops[37]).save(ROOT / 'menu' / 'gear_matriz.png')
to_32(crops[34]).save(ROOT / 'menu' / 'gear_nexo.png')

print('Action and gear textures updated.')
