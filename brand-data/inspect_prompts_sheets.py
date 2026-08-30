from PIL import Image
import numpy as np

im = Image.open('brand-data/prompts/05-iconos-ward.png')
print('05-iconos-ward size:', im.size)

# Also let's inspect Dreamcraft-menu-sprites.png
im2 = Image.open('brand-data/prompts/Dreamcraft-menu-sprites.png')
print('Dreamcraft-menu-sprites size:', im2.size)

# And Dreamcraft-logo-sprites.png
im3 = Image.open('brand-data/prompts/Dreamcraft-logo-sprites.png')
print('Dreamcraft-logo-sprites size:', im3.size)
