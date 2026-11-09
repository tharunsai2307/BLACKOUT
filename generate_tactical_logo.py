import math
from PIL import Image, ImageDraw, ImageFilter, ImageFont
import os

def create_tactical_logo(size=512):
    # Create RGBA canvas
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    center_x = size / 2.0
    center_y = size / 2.0
    radius = size * 0.46

    # 1. Background Rounded Octagonal Shield / Dark Slate Badge
    bg_color = (10, 16, 28, 255) # Deep navy black
    # Draw soft glow in background
    glow_img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    glow_draw = ImageDraw.Draw(glow_img)
    glow_draw.ellipse([size * 0.15, size * 0.15, size * 0.85, size * 0.85], fill=(6, 182, 212, 50))
    glow_draw.ellipse([size * 0.25, size * 0.25, size * 0.75, size * 0.75], fill=(239, 68, 68, 70))
    glow_img = glow_img.filter(ImageFilter.GaussianBlur(radius=25))
    img.paste(glow_img, (0, 0), glow_img)

    # 2. Main Shield / Hexagonal Emblem Background
    points = []
    # 8-sided tactical shield
    w_pad = size * 0.08
    top_w = size * 0.42
    shield_pts = [
        (center_x, size * 0.08),                 # Top center point
        (size - w_pad, size * 0.20),             # Top right corner
        (size - w_pad, size * 0.62),             # Mid right
        (center_x, size * 0.92),                 # Bottom point
        (w_pad, size * 0.62),                     # Mid left
        (w_pad, size * 0.20),                     # Top left corner
    ]
    
    # Outer Shield Stroke (Cyan / Chrome Gradient)
    draw.polygon(shield_pts, fill=(11, 18, 32, 250), outline=(6, 182, 212, 255))
    
    # Inner Inset Shield
    inner_pad = size * 0.025
    inner_shield = [
        (center_x, size * 0.08 + inner_pad * 1.5),
        (size - w_pad - inner_pad, size * 0.20 + inner_pad * 0.7),
        (size - w_pad - inner_pad, size * 0.62 - inner_pad * 0.5),
        (center_x, size * 0.92 - inner_pad * 1.5),
        (w_pad + inner_pad, size * 0.62 - inner_pad * 0.5),
        (w_pad + inner_pad, size * 0.20 + inner_pad * 0.7),
    ]
    draw.polygon(inner_shield, fill=(15, 23, 42, 255), outline=(30, 41, 59, 255))

    # 3. Concentric Mesh Radar Wave Arcs (Autonomous P2P connectivity)
    # Radiating upward & outward from the beacon core
    radar_center_x = center_x
    radar_center_y = size * 0.52

    # Draw 3 concentric radar wave arcs
    arc_radii = [size * 0.18, size * 0.26, size * 0.34]
    arc_widths = [4, 3, 2]
    arc_colors = [(6, 182, 212, 240), (6, 182, 212, 170), (6, 182, 212, 100)]

    for r, w, col in zip(arc_radii, arc_widths, arc_colors):
        bbox = [radar_center_x - r, radar_center_y - r * 0.75, radar_center_x + r, radar_center_y + r * 1.25]
        draw.arc(bbox, start=200, end=340, fill=col, width=int(w * size / 512))

    # 4. Central Emergency Flash Beacon (Ruby / Crimson Neon Core)
    core_rad = size * 0.12
    # Beacon base pedestal
    pedestal = [
        (center_x - core_rad * 1.1, radar_center_y + core_rad * 0.4),
        (center_x + core_rad * 1.1, radar_center_y + core_rad * 0.4),
        (center_x + core_rad * 0.8, radar_center_y + core_rad * 1.0),
        (center_x - core_rad * 0.8, radar_center_y + core_rad * 1.0),
    ]
    draw.polygon(pedestal, fill=(30, 41, 59, 255), outline=(51, 65, 85, 255))

    # Glowing Red Emergency Siren Dome
    dome_box = [center_x - core_rad * 0.85, radar_center_y - core_rad * 0.9, center_x + core_rad * 0.85, radar_center_y + core_rad * 0.5]
    draw.chord(dome_box, start=180, end=360, fill=(239, 68, 68, 255), outline=(248, 113, 113, 255))
    
    # 3D Highlight specular reflection on dome
    spec_box = [center_x - core_rad * 0.45, radar_center_y - core_rad * 0.75, center_x + core_rad * 0.15, radar_center_y - core_rad * 0.1]
    draw.chord(spec_box, start=180, end=360, fill=(254, 202, 202, 180))

    # Light Rays beaming outward
    for angle in [-40, -20, 0, 20, 40]:
        rad_ang = math.radians(angle - 90)
        x1 = center_x + math.cos(rad_ang) * (core_rad * 1.05)
        y1 = (radar_center_y - core_rad * 0.2) + math.sin(rad_ang) * (core_rad * 1.05)
        x2 = center_x + math.cos(rad_ang) * (core_rad * 1.6)
        y2 = (radar_center_y - core_rad * 0.2) + math.sin(rad_ang) * (core_rad * 1.6)
        draw.line([(x1, y1), (x2, y2)], fill=(252, 165, 165, 230), width=int(3 * size / 512))

    # 5. Downward Directed Transmission Wedge / Arrow (Mesh Broadcast Grounding)
    wedge_top_y = radar_center_y + core_rad * 1.15
    wedge = [
        (center_x - core_rad * 0.5, wedge_top_y),
        (center_x + core_rad * 0.5, wedge_top_y),
        (center_x, wedge_top_y + core_rad * 0.9),
    ]
    draw.polygon(wedge, fill=(6, 182, 212, 255), outline=(255, 255, 255, 200))

    # 6. Sleek Professional Typography: "BLACKOUT"
    # Modern stencil emergency typeface style
    try:
        font = ImageFont.truetype("arialbd.ttf", int(36 * size / 512))
        font_sub = ImageFont.truetype("arial.ttf", int(13 * size / 512))
    except:
        font = ImageFont.load_default()
        font_sub = ImageFont.load_default()

    text_main = "BLACKOUT"
    text_sub = "DEFENSE MESH"

    # Draw main title
    bbox = draw.textbbox((0, 0), text_main, font=font)
    t_w = bbox[2] - bbox[0]
    draw.text((center_x - t_w / 2.0, size * 0.77), text_main, fill=(255, 255, 255, 255), font=font)

    # Draw subtitle
    bbox_sub = draw.textbbox((0, 0), text_sub, font=font_sub)
    t_sub_w = bbox_sub[2] - bbox_sub[0]
    draw.text((center_x - t_sub_w / 2.0, size * 0.85), text_sub, fill=(6, 182, 212, 230), font=font_sub)

    return img

def export_all():
    print("Generating Master 512x512 Tactical Logo...")
    master = create_tactical_logo(512)
    
    # 1. Web Public Logo
    os.makedirs("web/public", exist_ok=True)
    master.save("web/public/logo.png", "PNG")
    print("Saved web/public/logo.png")

    # 2. Android App Foreground & In-App Logo
    os.makedirs("app/src/main/res/drawable", exist_ok=True)
    master.save("app/src/main/res/drawable/ic_launcher_foreground.png", "PNG")
    print("Saved app/src/main/res/drawable/ic_launcher_foreground.png")
    
    in_app_logo = master.resize((256, 256), Image.LANCZOS)
    in_app_logo.save("app/src/main/res/drawable/ic_blackout_logo.png", "PNG")
    print("Saved app/src/main/res/drawable/ic_blackout_logo.png")

    # 3. Android Mipmap densities
    mipmaps = {
        "mipmap-mdpi": 48,
        "mipmap-hdpi": 72,
        "mipmap-xhdpi": 96,
        "mipmap-xxhdpi": 144,
        "mipmap-xxxhdpi": 192,
    }

    for folder, dim in mipmaps.items():
        dest_dir = os.path.join("app/src/main/res", folder)
        os.makedirs(dest_dir, exist_ok=True)
        
        # Standard icon
        resized = master.resize((dim, dim), Image.LANCZOS)
        resized.save(os.path.join(dest_dir, "ic_launcher.png"), "PNG")

        # Round icon (create circular masked version)
        mask = Image.new("L", (dim, dim), 0)
        mask_draw = ImageDraw.Draw(mask)
        mask_draw.ellipse([1, 1, dim - 2, dim - 2], fill=255)
        
        round_img = Image.new("RGBA", (dim, dim), (0, 0, 0, 0))
        round_img.paste(resized, (0, 0))
        round_img.putalpha(mask)
        round_img.save(os.path.join(dest_dir, "ic_launcher_round.png"), "PNG")
        print(f"Saved {folder}/ic_launcher.png and ic_launcher_round.png ({dim}x{dim})")

    print("All professional tactical icons generated successfully!")

if __name__ == "__main__":
    export_all()
