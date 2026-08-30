from PIL import Image
import numpy as np

for c in range(4):
    for r in range(3):
        p = f'brand-data/iconos2_12/row{r}_col{c}.png'
        im = Image.open(p).convert('L').resize((16, 16), Image.NEAREST)
        arr = np.array(im)
        chars = ' .:-=+*#%@'
        print(f'=== ROW {r} COL {c} ===')
        for y in range(16):
            line = ''.join(chars[val // 26] for val in arr[y])
            print(line)
