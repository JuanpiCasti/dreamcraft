from PIL import Image
import numpy as np

# In assemble_designer_panel:
# target_w = 178, target_h = 141
# Minecraft vanilla 6-row container slots start at x=7, y=17
# Each slot is 18x18 px.
# Row 0: y = 18..35
# Row 1: y = 36..53
# Row 2: y = 54..71
# Row 3: y = 72..89
# Row 4: y = 90..107
# Row 5: y = 108..125
# Col 0..8: x = 8 + col * 18
# Let's inspect the layout of bg_menu_ward_status in regen_menu_backgrounds.py:
# R4-5:
#   identidad@36: col 0, row 4
#   permisos@37: col 1, row 4
#   transferir@39: col 3, row 4
#   Matriz@40: col 4..5, row 4..5
#   apagar@43: col 7, row 4
#   cerrar@44: col 8, row 4
print('Row 4 slots: 36, 37, 38, 39, 40, 41, 42, 43, 44')
print('Row 5 slots: 45, 46, 47, 48, 49, 50, 51, 52, 53')
