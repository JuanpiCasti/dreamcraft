from PIL import Image

im = Image.open('brand-data/prompts/iconos2.png')
prev = im.resize((im.size[0] // 2, im.size[1] // 2), Image.LANCZOS)
bg = Image.new('RGBA', prev.size, (25, 25, 30, 255))
bg.alpha_composite(prev)
bg.save('brand-data/prompts/iconos2_preview.png')
print('iconos2_preview.png saved.')
