from PIL import Image
import numpy as np
import glob

for idx in [32, 33, 35, 36, 41, 42]:
    files = glob.glob(f'brand-data/prompts/icons_extracted/icon_{idx:02d}_*.png')
    if files:
        im = Image.open(files[0]).convert('L').resize((20, 20), Image.NEAREST)
        arr = np.array(im)
        chars = ' .:-=+*#%@'
        print(f'=== ICON {idx} ===')
        for y in range(20):
            line = ''.join(chars[val // 26] for val in arr[y])
            print(line)
