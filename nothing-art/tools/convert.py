"""Converts Nothing Weather's widget animations into Glyph Weather's .gwf frame files.

Getting the source files (they are not in this repository):

    adb shell pm path com.nothing.weather        # prints the path of base.apk
    adb pull <that path> nothing-weather.apk
    unzip nothing-weather.apk 'res/raw/*_initial.json' -d nothing-weather

Then, from the repository root:

    python3 nothing-art/tools/convert.py nothing-weather/res/raw nothing-art/src/main/resources/nothing-art

Writes two files per animation:

  full/<name>.gwf   25x25, scaled to fill the round matrix
  small/<name>.gwf  12x12, one dot per LED, for the icon above the temperature

Both run at about 10 fps: each output frame is the average of 6 source frames (96 ms).
Every animation is centred on its last frame, the still image it ends on. Brightness is
scaled so the brightest dot in the animation is 255.

.gwf layout: b"GWF2", u8 width, u8 height, u16 frame count, u16 frame ms (big-endian), then
width * height bytes per frame, row by row.
"""
import math
import os
import struct
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import dotfile  # noqa: E402

# Nothing Weather file name -> output name. Only animations Glyph Weather uses are converted.
SOURCES = {
    "widget_clearday": "clearday",
    "widget_clearnight": "clearnight",
    "widget_partlycloudy": "partlycloudy",
    "widget_cloudynight": "cloudynight",
    "widget_overcast": "overcast",
    "widget_fog": "fog",
    "widget_rainy": "rainy",
    "widget_sleet": "sleet",
    "widget_snow": "snow",
    "widget_thunderstorm": "thunderstorm",
    "widget_windy": "windy",
    "weather_extremeheat": "extremeheat",
    "weather_extremecold": "extremecold",
}

SIZE, CENTER, RADIUS_SQ = 25, 12, 153  # matches icons/.../Matrix.kt
SMALL = 12                             # matches SmallIcons.SIZE
FRAMES_PER_OUTPUT = 6
SUPERSAMPLE = 4


def is_led(x, y):
    return (x - CENTER) ** 2 + (y - CENTER) ** 2 <= RADIUS_SQ


def lit_bounds(frames):
    xs, ys = [], []
    for frame in frames:
        for y, row in enumerate(frame):
            for x, v in enumerate(row):
                if v:
                    xs.append(x)
                    ys.append(y)
    return min(xs), min(ys), max(xs), max(ys)


def fit_scale(frames, cx, cy):
    """Largest scale at which every dot lit in any frame stays inside the LED circle."""
    reach = 0.0
    for frame in frames:
        for y, row in enumerate(frame):
            for x, v in enumerate(row):
                if v:
                    for dx in (-0.5, 0.5):
                        for dy in (-0.5, 0.5):
                            reach = max(reach, math.hypot(x + dx - cx, y + dy - cy))
    return (math.sqrt(RADIUS_SQ) + 0.5) / reach


def scale_to_matrix(frame, cx, cy, scale):
    """Area-average resample onto the 25x25 matrix; cells without an LED stay 0."""
    h, w = len(frame), len(frame[0])
    out = [[0.0] * SIZE for _ in range(SIZE)]
    for ty in range(SIZE):
        for tx in range(SIZE):
            if not is_led(tx, ty):
                continue
            total = 0.0
            for sy in range(SUPERSAMPLE):
                for sx in range(SUPERSAMPLE):
                    px = tx + (sx + 0.5) / SUPERSAMPLE - 0.5
                    py = ty + (sy + 0.5) / SUPERSAMPLE - 0.5
                    u = math.floor((px - CENTER) / scale + cx + 0.5)
                    v = math.floor((py - CENTER) / scale + cy + 0.5)
                    if 0 <= u < w and 0 <= v < h:
                        total += frame[v][u]
            out[ty][tx] = total / SUPERSAMPLE ** 2
    return out


def crop_small(frames, cx, cy):
    """One source dot per LED in a SMALL x SMALL square, with the art's centre on column 6.

    The square sits on matrix columns 6-17, so column 6 of the square is the matrix's centre
    column 12. Centring in the square itself (5.5) would put every icon half a column left.
    """
    h, w = len(frames[0]), len(frames[0][0])
    ox = math.floor(cx - SMALL // 2)
    oy = math.floor(cy - SMALL // 2)
    for frame in frames:
        for y, row in enumerate(frame):
            for x, v in enumerate(row):
                assert not v or (0 <= x - ox < SMALL and 0 <= y - oy < SMALL), "small icon would be cropped"
    return [[[frame[y + oy][x + ox] if 0 <= y + oy < h and 0 <= x + ox < w else 0
              for x in range(SMALL)] for y in range(SMALL)] for frame in frames]


def retime(frames):
    out = []
    for i in range(0, len(frames), FRAMES_PER_OUTPUT):
        chunk = frames[i:i + FRAMES_PER_OUTPUT]
        h, w = len(chunk[0]), len(chunk[0][0])
        out.append([[sum(f[y][x] for f in chunk) / len(chunk) for x in range(w)] for y in range(h)])
    return out


def drop_leading_blanks(frames):
    """The source files start with up to 20 empty frames; skip them so the intro starts at once."""
    first = next(i for i, f in enumerate(frames) if any(any(row) for row in f))
    return frames[first:]


def write(path, frames, peak):
    h, w = len(frames[0]), len(frames[0][0])
    with open(path, "wb") as f:
        f.write(b"GWF2" + struct.pack(">BBHH", w, h, len(frames), 16 * FRAMES_PER_OUTPUT))
        for frame in frames:
            f.write(bytes(min(255, round(255 * v / peak)) for row in frame for v in row))


def main():
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    source_dir, out_dir = sys.argv[1], sys.argv[2]
    for sub in ("full", "small"):
        os.makedirs(os.path.join(out_dir, sub), exist_ok=True)
    for source, name in SOURCES.items():
        _, frames = dotfile.load(os.path.join(source_dir, f"{source}_initial.json"))
        frames = drop_leading_blanks(frames)
        x0, y0, x1, y1 = lit_bounds(frames[-1:])
        cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
        peak = max(v for f in frames for row in f for v in row)

        scale = fit_scale(frames, cx, cy)
        full = retime([scale_to_matrix(f, cx, cy, scale) for f in frames])
        write(os.path.join(out_dir, "full", f"{name}.gwf"), full, peak)

        small = retime(crop_small(frames, cx, cy))
        write(os.path.join(out_dir, "small", f"{name}.gwf"), small, peak)
        print(f"{name:13} scale {scale:.2f}  {len(full)} frames")


main()
