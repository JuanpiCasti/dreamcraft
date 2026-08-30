from PIL import Image
import numpy as np

for name in ['05-iconos-ward.png', '06-iconos-city.png', 'Dreamcraft-menu-sprites.png']:
    im = Image.open(f'brand-data/prompts/{name}')
    thumb = im.copy()
    thumb.thumbnail((300, 300))
    thumb.save(f'brand-data/{name}_thumb.png')
print('Thumbnails saved.')
