from PIL import Image
import numpy as np

im = Image.open('brand-data/prompts/city_icon_original.png')
print('city_icon_original size:', im.size)
c = im.resize((20, 20), Image.LANCZOS)
arr = np.array(c)
bright = arr[:,:,3] > 60
for row in bright:
    print(''.join(['#' if p else ' ' for p in row]))
