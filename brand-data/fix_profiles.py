from PIL import Image
from pathlib import Path

ROOT = Path('resource-packs/packs/dreamcraft/assets/dreamcraft/textures/item/menu')

def make_clean_profile(src_path, dst_path):
    im = Image.open(src_path).convert('RGBA')
    # Resize to 32x32 clean
    thumb = im.resize((32, 32), Image.LANCZOS)
    thumb.save(dst_path)
    print(f'Saved clean profile to {dst_path.name}')

make_clean_profile('brand-data/iconos2_12/row2_col0.png', ROOT / 'profile_sync.png')
make_clean_profile('brand-data/iconos2_12/row2_col0.png', ROOT / 'profile.png')
make_clean_profile('brand-data/iconos2_12/row0_col0.png', ROOT / 'profile_matriz.png')
make_clean_profile('brand-data/iconos2_12/row1_col0.png', ROOT / 'profile_nexo.png')
