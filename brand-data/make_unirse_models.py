import json
from pathlib import Path

Q_MODELS = Path('resource-packs/packs/dreamcraft/assets/dreamcraft/models/item/menu/q')
for quad in ['tl', 'tr', 'bl', 'br']:
    model = {
        "parent": "minecraft:item/generated",
        "textures": {
            "layer0": f"dreamcraft:item/menu/q/unirse_{quad}"
        },
        "display": {
            "gui": {
                "rotation": [0, 0, 0],
                "translation": [0, 0, 0],
                "scale": [1.125, 1.125, 1.125]
            }
        }
    }
    (Q_MODELS / f'unirse_{quad}.json').write_text(json.dumps(model, indent=2))
print('Created unirse_tl..br models.')
