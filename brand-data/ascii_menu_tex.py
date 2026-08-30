from PIL import Image
import numpy as np

for name in ['profile.png', 'profile_sync.png', 'kick.png', 'close.png']:
    im = Image.open(f'resource-packs/packs/dreamcraft/assets/dreamcraft/textures/item/menu/{name}').convert('L').resize((20, 20), Image.NEAREST)
    arr = np.array(im)
    chars = ' .:-=+*#%@'
    print(f'=== {name} ===')
    for y in range(20):
        line = ''.join(chars[val // 26] for val in arr[y])
        print(line)
