from PIL import Image
import numpy as np
from pathlib import Path

crops = sorted(list(Path('brand-data/prompts/icons_extracted').glob('*.png')))

for idx, c in enumerate(crops):
    im = Image.open(c).convert('RGBA')
    arr = np.array(im)
    alpha = arr[:, :, 3] > 40
    rgb = arr[:, :, :3]
    
    # Analyze brightness and colors in central area (30..130)
    center = alpha[30:130, 30:130]
    crgb = rgb[30:130, 30:130]
    
    # Check for strong cross (diagonal lines)
    # Check for strong horizontal/vertical lines (plus sign)
    # Check for circular/shield boundary
    
    # Dominant theme by color:
    # Blue: Matriz
    # Purple/Violet: Nexo
    # Steel/Cyan: Sync/Synt
    # Gold: Treasury/Score/Tier
    # Red: Close/Kick/Disband
    
    c_blue = np.count_nonzero((crgb[:,:,2] > 120) & (crgb[:,:,0] < 80) & center)
    c_purp = np.count_nonzero((crgb[:,:,0] > 90) & (crgb[:,:,2] > 110) & (crgb[:,:,1] < 80) & center)
    c_cyan = np.count_nonzero((crgb[:,:,1] > 120) & (crgb[:,:,2] > 140) & (crgb[:,:,0] < 100) & center)
    c_gold = np.count_nonzero((crgb[:,:,0] > 150) & (crgb[:,:,1] > 110) & (crgb[:,:,2] < 70) & center)
    c_red = np.count_nonzero((crgb[:,:,0] > 140) & (crgb[:,:,1] < 70) & (crgb[:,:,2] < 70) & center)
    c_white = np.count_nonzero((crgb[:,:,0] > 180) & (crgb[:,:,1] > 180) & (crgb[:,:,2] > 180) & center)
    
    theme = 'Unknown'
    if c_red > 1000:
        theme = 'RED (Close/Kick/Danger)'
    elif c_gold > 1000:
        theme = 'GOLD (Treasury/Tier/Score)'
    elif c_purp > 1000:
        theme = 'PURPLE (Nexo/Estate)'
    elif c_blue > 1000:
        theme = 'BLUE (Matriz/City)'
    elif c_cyan > 1000:
        theme = 'CYAN (Sync/Ward)'
    
    print(f'#{idx:02d} {c.stem:22s} -> {theme:25s} | R:{c_red:4d} G:{c_gold:4d} B:{c_blue:4d} P:{c_purp:4d} C:{c_cyan:4d} W:{c_white:4d}')
