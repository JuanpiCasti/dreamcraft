from PIL import Image
import numpy as np

im_old = Image.open('brand-data/prompts/Dreamcraft-menu-sprites.png')
im_new = Image.open('brand-data/prompts/updatedsprites.png')

# Print out some thumbnails or slices
print('im_old:', im_old.size)
print('im_new:', im_new.size)
