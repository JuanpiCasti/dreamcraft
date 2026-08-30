from PIL import Image
from pathlib import Path

ROOT = Path('resource-packs/packs/dreamcraft/assets/dreamcraft/textures/item/menu')

def make_invite(src_path, dst_path):
    im = Image.open(src_path).convert('RGBA')
    thumb = im.resize((32, 32), Image.LANCZOS)
    thumb.save(dst_path)
    print(f'Saved proper invite (+ symbol) to {dst_path.name}')

make_invite('brand-data/iconos2_12/row0_col2.png', ROOT / 'invite_matriz.png')
make_invite('brand-data/iconos2_12/row0_col2.png', ROOT / 'invite.png')
make_invite('brand-data/iconos2_12/row1_col2.png', ROOT / 'invite_nexo.png')
make_invite('brand-data/iconos2_12/row2_col2.png', ROOT / 'invite_sync.png')
