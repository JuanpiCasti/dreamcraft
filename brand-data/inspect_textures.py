from PIL import Image
from pathlib import Path

ROOT = Path('resource-packs/packs/dreamcraft/assets/dreamcraft/textures/item/menu')
for name in ['profile.png', 'profile_sync.png', 'profile_matriz.png', 'profile_nexo.png', 'kick.png', 'close.png', 'back.png', 'gear.png', 'gear_sync.png']:
    p = ROOT / name
    if p.exists():
        print(f'{name}: size={Image.open(p).size}')
