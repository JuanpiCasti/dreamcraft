from PIL import Image, ImageDraw

im = Image.open('brand-data/prompts/updatedsprites.png')
# Save 50% scaled preview
prev = im.resize((im.size[0] // 2, im.size[1] // 2), Image.LANCZOS)
bg = Image.new('RGBA', prev.size, (20, 20, 25, 255))
bg.alpha_composite(prev)
bg.save('brand-data/prompts/updatedsprites_preview.png')
print('Preview saved.')
