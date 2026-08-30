from PIL import Image
import numpy as np

im = Image.open('brand-data/prompts/10-bloque-synt.png').convert('RGBA')
# Object 0: (59, 47, 828, 816)
face_act = im.crop((59, 47, 828, 816))
# Object 1: (946, 48, 1715, 816)
face_inact = im.crop((946, 48, 1715, 816))

# Let's inspect the edges of face_act:
arr = np.array(face_act)
print('Face active size:', face_act.size)
print('Top row alpha mean:', arr[0, :, 3].mean())
print('Bottom row alpha mean:', arr[-1, :, 3].mean())
print('Left col alpha mean:', arr[:, 0, 3].mean())
print('Right col alpha mean:', arr[:, -1, 3].mean())
# Are the outer pixels transparent or semitransparent?
print('Inner pixels alpha mean (y=10..-10, x=10..-10):', arr[10:-10, 10:-10, 3].mean())
