import os
import math
from PIL import Image, ImageDraw, ImageFont, ImageFilter

W, H = 840, 460
FPS = 12
DURATION_SEC = 6
TOTAL_FRAMES = FPS * DURATION_SEC

# Load screenshots and resize to phone mock height
phone_h = 400
aspect = 462 / 1024
phone_w = int(phone_h * aspect)  # ~180

screens = {
    'splash': Image.open('assets/screenshots/splash.jpg').resize((phone_w, phone_h), Image.Resampling.LANCZOS),
    'home': Image.open('assets/screenshots/home.jpg').resize((phone_w, phone_h), Image.Resampling.LANCZOS),
    'downloads': Image.open('assets/screenshots/downloads.jpg').resize((phone_w, phone_h), Image.Resampling.LANCZOS),
    'settings': Image.open('assets/screenshots/settings.jpg').resize((phone_w, phone_h), Image.Resampling.LANCZOS),
}

# Rounded corners for phone screen
mask = Image.new('L', (phone_w, phone_h), 0)
mask_draw = ImageDraw.Draw(mask)
mask_draw.rounded_rectangle([0, 0, phone_w, phone_h], radius=24, fill=255)

for k in screens:
    screens[k].putalpha(mask)

# Mascot
crab = Image.open('assets/logo.png').convert('RGBA').resize((110, 110), Image.Resampling.LANCZOS)

# Try default or standard fonts
font_title = None
font_sub = None
font_badge = None
font_step = None
font_paths = [
    'C:/Windows/Fonts/segoeui.ttf',
    'C:/Windows/Fonts/arial.ttf',
    'C:/Windows/Fonts/calibri.ttf'
]
for fp in font_paths:
    if os.path.exists(fp):
        try:
            font_title = ImageFont.truetype(fp, 32)
            font_sub = ImageFont.truetype(fp, 16)
            font_step = ImageFont.truetype(fp, 15)
            font_badge = ImageFont.truetype(fp, 13)
            break
        except Exception:
            pass

if not font_title:
    font_title = ImageFont.load_default()
    font_sub = ImageFont.load_default()
    font_step = ImageFont.load_default()
    font_badge = ImageFont.load_default()

steps = [
    ("⚡ Instant Analysis", "Paste any Instagram, YouTube or FB link"),
    ("🎬 Video & Audio Tabs", "Real qualities: 1080p, 720p, MP4, WebM & MP3"),
    ("📥 Offline Library", "Built-in media player, search & filters"),
    ("🎨 Material You Themes", "Light, Dark, AMOLED & 6 custom palettes")
]

screen_sequence = [
    screens['home'],
    screens['downloads'],
    screens['settings'],
    screens['home']
]

frames = []

for i in range(TOTAL_FRAMES):
    t = i / TOTAL_FRAMES
    bg = Image.new('RGB', (W, H), (255, 250, 248))
    draw = ImageDraw.Draw(bg)

    # Subtle gradient / decorative background elements
    # Soft warm circles
    glow_x = int(W * 0.75 + math.sin(t * math.pi * 2) * 20)
    glow_y = int(H * 0.5 + math.cos(t * math.pi * 2) * 15)
    draw.ellipse([glow_x - 180, glow_y - 180, glow_x + 180, glow_y + 180], fill=(255, 236, 230))
    draw.ellipse([80, 80, 260, 260], fill=(255, 242, 238))

    # Left Column: Branding + Animated Highlights
    # Mascot float animation
    bounce = int(math.sin(t * math.pi * 4) * 4)
    bg.paste(crab, (48, 40 + bounce), crab)

    draw.text((172, 48 + bounce), "Tiny Crap", fill=(30, 35, 45), font=font_title)
    draw.text((174, 90 + bounce), "Smart Media Downloader for Android", fill=(120, 100, 95), font=font_sub)

    # Active step index (0 to 3)
    curr_step_idx = int(t * 4) % 4
    step_y = 175

    for s_idx, (stitle, sdesc) in enumerate(steps):
        is_active = (s_idx == curr_step_idx)
        card_y = step_y + s_idx * 62
        
        if is_active:
            # Active pill
            draw.rounded_rectangle([48, card_y, 440, card_y + 52], radius=12, fill=(255, 228, 220), outline=(234, 78, 86), width=2)
            # Dot indicator
            draw.ellipse([64, card_y + 18, 76, card_y + 30], fill=(234, 78, 86))
            draw.text((88, card_y + 8), stitle, fill=(180, 40, 50), font=font_step)
            draw.text((88, card_y + 28), sdesc, fill=(110, 80, 75), font=font_badge)
        else:
            draw.rounded_rectangle([48, card_y, 440, card_y + 52], radius=12, fill=(255, 255, 255), outline=(245, 230, 225), width=1)
            draw.ellipse([64, card_y + 20, 74, card_y + 30], fill=(210, 190, 185))
            draw.text((88, card_y + 8), stitle, fill=(80, 85, 95), font=font_step)
            draw.text((88, card_y + 28), sdesc, fill=(150, 140, 135), font=font_badge)

    # Right Column: Phone Screen with Phone Shadow & Frame
    # Current screen transition
    curr_scr = screen_sequence[curr_step_idx]
    next_scr = screen_sequence[(curr_step_idx + 1) % len(screen_sequence)]
    
    # Progress within this step (0.0 to 1.0)
    sub_t = (t * 4) % 1.0
    
    phone_x = 540
    phone_y = 30

    # Phone drop shadow
    draw.rounded_rectangle([phone_x + 8, phone_y + 12, phone_x + phone_w + 14, phone_y + phone_h + 16], radius=28, fill=(235, 215, 210))
    # Phone border frame
    draw.rounded_rectangle([phone_x - 6, phone_y - 6, phone_x + phone_w + 6, phone_y + phone_h + 6], radius=30, fill=(35, 38, 45))
    
    # Screen blend if transitioning in the last 20% of sub_t
    if sub_t > 0.8:
        blend_alpha = (sub_t - 0.8) / 0.2
        # Crossfade
        blended = Image.blend(curr_scr.convert('RGB'), next_scr.convert('RGB'), blend_alpha).convert('RGBA')
        blended.putalpha(mask)
        bg.paste(blended, (phone_x, phone_y), blended)
    else:
        bg.paste(curr_scr, (phone_x, phone_y), curr_scr)

    # Phone camera notch / speaker bar
    draw.rounded_rectangle([phone_x + phone_w // 2 - 24, phone_y + 6, phone_x + phone_w // 2 + 24, phone_y + 16], radius=5, fill=(35, 38, 45))

    frames.append(bg.quantize(colors=128))

# Save GIF
output_path = 'assets/preview.gif'
frames[0].save(
    output_path,
    save_all=True,
    append_images=frames[1:],
    optimize=True,
    duration=int(1000 / FPS),
    loop=0
)

print(f'Generated motion graphic GIF: {output_path} ({os.path.getsize(output_path) / 1024:.1f} KB)')
