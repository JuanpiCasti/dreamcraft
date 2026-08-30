import json
from pathlib import Path

p = Path('resource-packs/packs/dreamcraft/assets/dreamcraft/models/item/menu/permissions.json')
m = {
    "parent": "minecraft:item/generated",
    "textures": {
        "layer0": "dreamcraft:item/menu/permissions"
    },
    "display": {
        "gui": {
            "rotation": [0, 0, 0],
            "translation": [0, 0, 0],
            "scale": [1.125, 1.125, 1.125]
        }
    }
}
p.write_text(json.dumps(m, indent=2))
print('Created permissions.json model.')
