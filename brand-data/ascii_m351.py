from PIL import Image
import numpy as np

im = Image.open('brand-data/prompts/menu_351_left.png').convert('L')
small = im.resize((45, 54), Image.LANCZOS)
arr = np.array(small, dtype=int)
chars = ' .:-=+*#%@'
for row in arr:
    line = ''.join([chars[min(len(chars)-1, p * len(chars) // 256)] for p in row])
    print(line)
