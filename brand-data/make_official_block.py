from PIL import Image
import numpy as np

im = Image.open('brand-data/prompts/10-bloque-synt.png').convert('RGB') # fully opaque RGB

# Active: x=65..827, y=49..814
crop_act = im.crop((65, 49, 827, 811)) # 762x762 square!
# Inactive: x=947..1709, y=49..811
crop_inact = im.crop((947, 49, 1709, 811)) # 762x762 square!

print('Crop active size:', crop_act.size)
print('Crop inactive size:', crop_inact.size)

# Resize to 32x32 using LANCZOS
tex_act_32 = crop_act.resize((32, 32), Image.LANCZOS)
tex_inact_32 = crop_inact.resize((32, 32), Image.LANCZOS)

# Save to destination
p_act = 'resource-packs/packs/dreamcraft/assets/dreamcraft/textures/block/nucleus_face_active.png'
p_inact = 'resource-packs/packs/dreamcraft/assets/dreamcraft/textures/block/nucleus_face_inactive.png'
p_item = 'resource-packs/packs/dreamcraft/assets/dreamcraft/textures/item/nucleus/icon.png'

tex_act_32.save(p_act)
tex_inact_32.save(p_inact)
tex_act_32.save(p_item)
print('Saved 100% solid 32x32 block textures without transparent seams.')
