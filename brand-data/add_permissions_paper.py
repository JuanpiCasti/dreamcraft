import json
from pathlib import Path

p = Path('resource-packs/packs/dreamcraft/assets/minecraft/items/paper.json')
data = json.loads(p.read_text())

entries = data['model']['entries']
# Check if 41415 already exists
existing = [e for e in entries if e['threshold'] == 41415]
if not existing:
    entries.append({
        "threshold": 41415,
        "model": {
            "type": "minecraft:model",
            "model": "dreamcraft:item/menu/permissions"
        }
    })
    entries.sort(key=lambda x: x['threshold'])
    data['model']['entries'] = entries
    p.write_text(json.dumps(data, indent=2))
    print('Added 41415 to paper.json.')
else:
    print('41415 already present.')
