import os
import re
from PIL import Image, ImageOps

os.makedirs('assets/screenshots', exist_ok=True)

# 1. Parse ic_splash_crab.xml to SVG
with open('frontend/app/src/main/res/drawable/ic_splash_crab.xml', 'r', encoding='utf-8') as f:
    xml_content = f.read()

# Match each path block
path_blocks = re.findall(r'<path\s+[^>]*/>', xml_content)
paths = []
for block in path_blocks:
    color_m = re.search(r'android:fillColor="([^"]+)"', block)
    data_m = re.search(r'android:pathData="([^"]+)"', block)
    if color_m and data_m:
        paths.append((color_m.group(1), data_m.group(1)))

svg = [
    '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" width="160" height="160">',
    '  <defs>',
    '    <linearGradient id="bgGrad" x1="0%" y1="0%" x2="100%" y2="100%">',
    '      <stop offset="0%" stop-color="#FFF5F2"/>',
    '      <stop offset="100%" stop-color="#FFD6CD"/>',
    '    </linearGradient>',
    '    <filter id="shadow" x="-20%" y="-20%" width="140%" height="140%">',
    '      <feDropShadow dx="0" dy="10" stdDeviation="14" flood-color="#EA4E56" flood-opacity="0.25"/>',
    '    </filter>',
    '  </defs>',
    '  <rect x="36" y="36" width="440" height="440" rx="110" fill="url(#bgGrad)" filter="url(#shadow)"/>',
    '  <g transform="translate(256, 250) scale(0.74) translate(-256, -250)">'
]

for color, d in paths:
    svg.append(f'    <path fill="{color}" d="{d}"/>')

svg.append('  </g>')
svg.append('</svg>')

with open('assets/logo.svg', 'w', encoding='utf-8') as f:
    f.write('\n'.join(svg))

print('Saved assets/logo.svg')

# 2. Extract crab mascot into high-quality PNG logo
splash_img = Image.open('assets/screenshots/splash.jpg').convert('RGBA')
# Crab bounds in 462x1024 splash: (136, 391, 325, 565)
crab_crop = splash_img.crop((125, 380, 337, 575))

# Make background transparent: splash background is #F7F7F7
datas = crab_crop.getdata()
new_data = []
for item in datas:
    # item is (r, g, b, a)
    r, g, b = item[:3]
    if r > 240 and g > 240 and b > 240:
        new_data.append((255, 255, 255, 0))
    else:
        new_data.append(item)

crab_crop.putdata(new_data)

# Create a beautiful 512x512 app icon
icon = Image.new('RGBA', (512, 512), (0, 0, 0, 0))

# Draw soft rounded squircle or circle background
from PIL import ImageDraw
draw = ImageDraw.Draw(icon)
# Rounded rectangle with peach gradient / soft background #FFF5F2
draw.rounded_rectangle([24, 24, 488, 488], radius=110, fill=(255, 244, 240, 255), outline=(255, 214, 205, 255), width=3)

# Paste crab centered and scaled
scaled_crab = crab_crop.resize((320, 290), Image.Resampling.LANCZOS)
crab_pos = ((512 - scaled_crab.width) // 2, (512 - scaled_crab.height) // 2)
icon.paste(scaled_crab, crab_pos, scaled_crab)

icon.save('assets/logo.png')
print('Saved assets/logo.png')
