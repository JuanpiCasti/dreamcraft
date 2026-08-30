import json
from pathlib import Path

MODELS = Path('resource-packs/packs/dreamcraft/assets/dreamcraft/models/item/menu')
MODELS.mkdir(parents=True, exist_ok=True)

items = [
    'back', 'profile', 'close', 'invite', 'kick', 'roles', 'members',
    'confirm', 'line', 'catcher', 'gear', 'gear_sync', 'gear_matriz', 'gear_nexo',
    'profile_matriz', 'profile_nexo', 'profile_sync',
    'roles_matriz', 'roles_nexo', 'roles_sync',
    'invite_matriz', 'invite_nexo', 'invite_sync',
    'join_matriz', 'join_nexo', 'join_sync'
]

for name in items:
    model_path = MODELS / f'{name}.json'
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
    model_path.write_text(json.dumps(m, indent=2))

print(f'Generated {len(items)} models in {MODELS}')
