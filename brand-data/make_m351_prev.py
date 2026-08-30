from PIL import Image

im = Image.open('brand-data/prompts/menus-351.png')
# Save 50% preview
im.resize((im.size[0]//2, im.size[1]//2), Image.LANCZOS).save('brand-data/prompts/menus_351_preview.png')
print('menus_351_preview.png saved.')
