from PIL import Image
import numpy as np

im_old = Image.open('brand-data/prompts/Dreamcraft-menu-sprites.png').convert('RGBA')
im_new = Image.open('brand-data/prompts/updatedsprites.png').convert('RGBA')

print('Old size:', im_old.size)
print('New size:', im_new.size)

# Let's inspect where in the new image the sprites are positioned
# We have 5 rows in the new sheet:
# Row 0: y in ~ 10..180
# Row 1: y in ~ 180..350
# Row 2: y in ~ 350..520
# Row 3: y in ~ 520..700
# Row 4: y in ~ 700..880

# Let's print out what each row corresponds to by inspecting the colors and visual elements:
