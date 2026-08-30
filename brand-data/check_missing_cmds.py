import json

data = json.load(open('resource-packs/packs/dreamcraft/assets/minecraft/items/paper.json'))
entries = data['model']['entries']
thresholds = [e['threshold'] for e in entries]
print('Thresholds in paper.json:', sorted(thresholds))

expected = [41402, 41403, 41404, 41405, 41406, 41407, 41408, 41409, 41410, 41411, 41412, 41413, 41414]
missing = [x for x in expected if x not in thresholds]
print('Missing CMDs in paper.json:', missing)
