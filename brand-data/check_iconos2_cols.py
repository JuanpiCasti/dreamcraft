from PIL import Image
import numpy as np

for c in range(4):
    im = Image.open(f'brand-data/iconos2_12/row2_col{c}.png').convert('L').resize((16, 16), Image.NEAREST)
    arr = np.array(im)
    chars = ' .:-=+*#%@'
    print(f'=== ROW2_COL{c} ===')
    for y in range(16):
        line = ''.join(chars[val // 26] for val in arr[y])
        print(line)
