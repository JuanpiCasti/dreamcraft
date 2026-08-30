from PIL import Image
import numpy as np
from pathlib import Path

ROOT = Path('resource-packs/packs/dreamcraft/assets/dreamcraft/textures/item')

# Let's inspect roles.png, roles_sync.png, invite.png, invite_sync.png, back.png, etc.
files = ['menu/roles.png', 'menu/roles_sync.png', 'menu/invite.png', 'menu/invite_sync.png', 'menu/invite_matriz.png', 'menu/join_sync.png', 'menu/join_matriz.png', 'menu/join_nexo.png']

for rel in files:
    p = ROOT / rel
    if p.exists():
        im = Image.open(p).convert('L').resize((16, 16), Image.NEAREST)
        arr = np.array(im)
        chars = ' .:-=+*#%@'
        print(f'=== {rel} ===')
        for y in range(16):
            line = ''.join(chars[val // 26] for val in arr[y])
            print(line)
