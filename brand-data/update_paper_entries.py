import json
from pathlib import Path

p = Path('resource-packs/packs/dreamcraft/assets/minecraft/items/paper.json')
data = json.loads(p.read_text())

# Map thresholds to models
cmds_41400 = [
    (41401, "dreamcraft:item/menu/confirm"), # deposit
    (41402, "dreamcraft:item/menu/back"),    # back / flecha izquierda
    (41403, "dreamcraft:item/menu/profile"), # profile
    (41404, "dreamcraft:item/menu/close"),   # close
    (41405, "dreamcraft:item/menu/invite"),  # invite
    (41406, "dreamcraft:item/menu/kick"),    # kick
    (41407, "dreamcraft:item/menu/roles"),   # roles / permissions (roles_sync)
    (41408, "dreamcraft:item/menu/members"), # members
    (41409, "dreamcraft:item/menu/confirm"), # confirm
    (41410, "dreamcraft:item/menu/line"),    # line
    (41411, "dreamcraft:item/menu/catcher"), # catcher
    (41412, "dreamcraft:item/menu/gear_sync"),# gear sync / ward disband
    (41413, "dreamcraft:item/menu/gear_matriz"), # gear matriz
    (41414, "dreamcraft:item/menu/gear_nexo"), # gear nexo
]

# Keep existing >= 41501
existing_41500 = [e for e in data['model']['entries'] if e['threshold'] >= 41500]

new_entries = []
for th, mod in cmds_41400:
    new_entries.append({
        "threshold": th,
        "model": {
            "type": "minecraft:model",
            "model": mod
        }
    })

new_entries.extend(existing_41500)
# Sort by threshold
new_entries.sort(key=lambda x: x['threshold'])

data['model']['entries'] = new_entries
p.write_text(json.dumps(data, indent=2))
print(f'Updated paper.json: total entries = {len(new_entries)}')
