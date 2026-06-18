#!/usr/bin/env python3
"""缩小小黄鸭在图标中的占比，并重新生成所有 mipmap 资源。"""

from pathlib import Path
from PIL import Image, ImageDraw

# 项目路径
APP_RES = Path(__file__).parent.parent / "app" / "src" / "main" / "res"

# Android 标准密度对应比例
DENSITIES = {
    "mipmap-xhdpi": 2,      # 108 dp * 2 = 216 px
    "mipmap-xxhdpi": 3,     # 108 dp * 3 = 324 px
    "mipmap-xxxhdpi": 4,    # 108 dp * 4 = 432 px
}

# 小黄鸭缩放比例（0.75 = 占原来 75%）
DUCK_SCALE = 0.75

# 背景色取自 ic_launcher_background.xml
BG_COLOR = (0x83, 0xD0, 0xF1, 0xFF)


def make_rounded_mask(size: int, radius_ratio: float = 0.22) -> Image.Image:
    """生成圆角矩形 mask（用于圆角方形图标）。"""
    mask = Image.new("L", (size, size), 0)
    draw = ImageDraw.Draw(mask)
    radius = int(size * radius_ratio)
    draw.rounded_rectangle((0, 0, size, size), radius=radius, fill=255)
    return mask


def make_circle_mask(size: int) -> Image.Image:
    """生成圆形 mask（用于圆形图标）。"""
    mask = Image.new("L", (size, size), 0)
    draw = ImageDraw.Draw(mask)
    draw.ellipse((0, 0, size, size), fill=255)
    return mask


def compose_icon(foreground: Image.Image, size: int, mask: Image.Image) -> Image.Image:
    """将前景合成到纯色背景上，并应用形状 mask。"""
    bg = Image.new("RGBA", (size, size), BG_COLOR)
    # 将前景缩放到当前密度尺寸
    fg = foreground.resize((size, size), Image.Resampling.LANCZOS)
    bg.alpha_composite(fg)
    # 应用形状 mask
    result = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    result.paste(bg, (0, 0), mask)
    return result.convert("RGB")


def main():
    # 读取最高分辨率前景图
    src_fg = Image.open(APP_RES / "mipmap-xxxhdpi" / "ic_launcher_foreground.webp").convert("RGBA")
    src_size = src_fg.size[0]

    # 缩放鸭子内容，增加透明边距
    new_size = int(src_size * DUCK_SCALE)
    fg_resized = src_fg.resize((new_size, new_size), Image.Resampling.LANCZOS)

    # 放置到原始尺寸透明画布中央
    foreground_scaled = Image.new("RGBA", (src_size, src_size), (0, 0, 0, 0))
    offset = (src_size - new_size) // 2
    foreground_scaled.paste(fg_resized, (offset, offset), fg_resized)

    for folder, scale in DENSITIES.items():
        px = 108 * scale  # 自适应图标前景标准尺寸
        folder_path = APP_RES / folder
        folder_path.mkdir(parents=True, exist_ok=True)

        # 生成新的前景 webp
        fg = foreground_scaled.resize((px, px), Image.Resampling.LANCZOS)
        fg.save(folder_path / "ic_launcher_foreground.webp", "WEBP", quality=95)

        # 生成合成后的圆角方形和圆形 PNG
        icon_size = 48 * scale  # 启动图标标准尺寸
        rounded_mask = make_rounded_mask(icon_size)
        circle_mask = make_circle_mask(icon_size)

        compose_icon(fg, icon_size, rounded_mask).save(
            folder_path / "ic_launcher.png", "PNG"
        )
        compose_icon(fg, icon_size, circle_mask).save(
            folder_path / "ic_launcher_round.png", "PNG"
        )

        print(f"Generated {folder}: {px}x{px} foreground, {icon_size}x{icon_size} icons")


if __name__ == "__main__":
    main()
