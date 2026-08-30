import json
from pathlib import Path

MODELS = Path('resource-packs/packs/dreamcraft/assets/dreamcraft/models/item/menu')
for name in ['gear', 'gear_sync', 'gear_matriz', 'gear_nexo']:
    m = {
        "parent": "minecraft:item/generated",
        "textures": {
            "layer0": f"dreamcraft:item/menu/{name}"
        },
        "display": {
            "gui": {
                "rotation": [0, 0, 0],
                "translation": [0, 0, 0],
                "scale": [1.125, 1.125, 1.125]
            }
        }
    }
    (MODELS / f'{name}.json').write_text(json.dumps(m, indent=2))
print('Gear models generated.')
