from PIL import Image
import numpy as np

im = Image.open('brand-data/prompts/sync_ui_bottom.png').convert('L')
# 50 cols, 30 rows
small = im.resize((50, 30), Image.LANCZOS)
arr = np.array(small, dtype=int)
chars = ' .:-=+*#%@'
for row in arr:
    line = ''.join([chars[min(len(chars)-1, p * len(chars) // 256)] for p in row])
    print(line)
