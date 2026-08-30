from PIL import Image
import numpy as np

im = Image.open('brand-data/prompts/05-iconos-ward.png').convert('RGBA')
boxes = [
    ('box0_top_left', (74, 60, 453, 476)),
    ('box1_top_mid', (625, 93, 902, 454)),
    ('box2_top_right', (1093, 61, 1477, 487)),
    ('box3_bot_left', (334, 548, 683, 957)),
    ('box4_bot_right', (841, 575, 1215, 959)),
]

for name, box in boxes:
    crop = im.crop(box)
    arr = np.array(crop)
    alpha = arr[:,:,3] > 40
    rgb = arr[:,:,:3]
    mean_c = rgb[alpha].mean(axis=0).astype(int)
    print(f'{name}: mean_rgb={mean_c}')
