from PIL import Image
import numpy as np

im = Image.open('brand-data/prompts/menus-351.png').convert('RGBA')
# The image is 1478x558. Let's slice the 3 menus:
# menu 1 (left): 0..490
# menu 2 (mid): 490..980
# menu 3 (right): 980..1478
m1 = im.crop((0, 0, 490, 558))
m2 = im.crop((490, 0, 980, 558))
m3 = im.crop((980, 0, 1478, 558))

m1.save('brand-data/prompts/menu_351_left.png')
m2.save('brand-data/prompts/menu_351_mid.png')
m3.save('brand-data/prompts/menu_351_right.png')
print('Sliced 3 menus.')
