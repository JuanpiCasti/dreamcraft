from PIL import Image
import numpy as np

for name in ['invite.png', 'invite_matriz.png', 'invite_sync.png', 'invite_nexo.png']:
    im = Image.open(f'resource-packs/packs/dreamcraft/assets/dreamcraft/textures/item/menu/{name}').convert('L').resize((16, 16), Image.NEAREST)
    arr = np.array(im)
    chars = ' .:-=+*#%@'
    print(f'=== {name} ===')
    for y in range(16):
        line = ''.join(chars[val // 26] for val in arr[y])
        print(line)
