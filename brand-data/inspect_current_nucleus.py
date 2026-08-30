from PIL import Image
p_act = 'resource-packs/packs/dreamcraft/assets/dreamcraft/textures/block/nucleus_face_active.png'
p_inact = 'resource-packs/packs/dreamcraft/assets/dreamcraft/textures/block/nucleus_face_inactive.png'
p_item = 'resource-packs/packs/dreamcraft/assets/dreamcraft/textures/item/nucleus/icon.png'

print('active block texture:', Image.open(p_act).size)
print('inactive block texture:', Image.open(p_inact).size)
print('item texture:', Image.open(p_item).size)
