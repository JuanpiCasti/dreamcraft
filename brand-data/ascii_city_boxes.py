from PIL import Image
import numpy as np

im = Image.open('brand-data/prompts/06-iconos-city.png').convert('RGBA')
# The 4 boxes:
boxes = {
    'top_left_treasury': (253, 29, 697, 505),
    'top_right_admin': (909, 50, 1320, 505),
    'bot_left_beacon': (249, 532, 705, 968),
    'bot_right_trophy': (906, 515, 1322, 981),
}

for k, b in boxes.items():
    c = im.crop(b)
    c.thumbnail((24, 24), Image.LANCZOS)
    arr = np.array(c)
    bright = arr[:,:,3] > 60
    print(f'=== {k} ===')
    for row in bright:
        print(''.join(['#' if p else ' ' for p in row]))
