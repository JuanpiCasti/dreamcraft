import json
from pathlib import Path

p_cube = Path('resource-packs/packs/dreamcraft/assets/dreamcraft/models/block/nucleus_cube.json')
p_cube_inact = Path('resource-packs/packs/dreamcraft/assets/dreamcraft/models/block/nucleus_cube_inactive.json')
p_icon = Path('resource-packs/packs/dreamcraft/assets/dreamcraft/models/item/nucleus/icon.json')

cube_model = {
    "parent": "minecraft:block/cube_all",
    "textures": {
        "all": "dreamcraft:block/nucleus_face_active"
    }
}
cube_inact_model = {
    "parent": "minecraft:block/cube_all",
    "textures": {
        "all": "dreamcraft:block/nucleus_face_inactive"
    }
}
icon_model = {
    "parent": "dreamcraft:block/nucleus_cube"
}

p_cube.write_text(json.dumps(cube_model, indent=2))
p_cube_inact.write_text(json.dumps(cube_inact_model, indent=2))
p_icon.write_text(json.dumps(icon_model, indent=2))

print('Updated block and item models to official minecraft:block/cube_all parent.')
