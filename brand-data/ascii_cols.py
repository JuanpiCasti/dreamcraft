# Let's inspect the shapes:
# Col 0: profile? (busto solo)
# Col 1: permissions? What is in col 1?
# Col 2: invite? (plus sign? two people?)
# Col 3: join? (arrow entering door/circle?)

# Let's print ASCII art or pixel patterns of the inner 16x16 of col 0, 1, 2, 3:
from PIL import Image
import numpy as np

for c in range(4):
    im = Image.open(f'brand-data/iconos2_12/row2_col{c}.png').convert('L').resize((24, 24), Image.LANCZOS)
    arr = np.array(im)
    chars = ' .:-=+*#%@'
    print(f'=== COLUMN {c} ===')
    for y in range(24):
        line = ''.join(chars[val // 26] for val in arr[y])
        print(line)
