from PIL import Image
import numpy as np

im = Image.open('brand-data/prompts/menu_351_left.png').convert('RGBA')
print('menu_351_left size:', im.size)
# Sample lower half (y > 250)
lower = im.crop((0, 250, im.size[0], im.size[1]))
lower.save('brand-data/prompts/menu_351_left_lower.png')
print('Saved lower half.')
