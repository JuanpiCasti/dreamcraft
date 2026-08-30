from PIL import Image
import os

im_ward = Image.open('resource-packs/packs/dreamcraft/assets/dreamcraft/textures/font/gui/bg_menu_ward_status.png')
im_city = Image.open('resource-packs/packs/dreamcraft/assets/dreamcraft/textures/font/gui/bg_menu_city_overview.png')

os.makedirs('brand-data/inspect_baked_slots', exist_ok=True)

# Grid layout in 178x141 / 178x139:
# Standard Minecraft inventory slot grid:
# x0 = 7, y0 = 17, slot pitch = 18 px
for slot in range(54):
    r = slot // 9
    c = slot % 9
    x = 8 + c * 18
    y = 18 + r * 18
    crop_w = im_ward.crop((x, y, x+16, y+16))
    crop_c = im_city.crop((x, y, x+16, y+16))
    
    # Save if non-empty
    import numpy as np
    if np.any(np.array(crop_w)[:,:,3] > 0):
        crop_w.save(f'brand-data/inspect_baked_slots/ward_slot_{slot:02d}.png')
    if np.any(np.array(crop_c)[:,:,3] > 0):
        crop_c.save(f'brand-data/inspect_baked_slots/city_slot_{slot:02d}.png')

print('Saved non-empty baked slots!')
