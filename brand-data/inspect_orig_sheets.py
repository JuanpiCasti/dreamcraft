from PIL import Image
p_city = 'brand-data/prompts/06-iconos-city.png'
p_est = 'brand-data/prompts/07-iconos-estate.png'

im_c = Image.open(p_city)
im_e = Image.open(p_est)
print('City icons image:', im_c.size, im_c.mode)
print('Estate icons image:', im_e.size, im_e.mode)
