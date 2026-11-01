"""Reader for the dot-grid animation files in the Nothing Weather app (res/raw/*_initial.json).

Each file is JSON with a `portionX` x `portionY` grid of dots and a `frameDots` list. Every
frame maps a dot index (row-major, `index = row * portionX + col`) to "radius:brightness".
Dots missing from a frame are off. Frames are 16 ms apart (about 60 fps).

In every file shipped with Nothing Weather 3.4.5 the radius is the same for all dots, so only
brightness (0-255) carries the picture.
"""
import json


def load(path):
    """Returns (header dict, frames), each frame a list of rows of brightness values."""
    with open(path) as f:
        data = json.load(f)
    width, height = data["portionX"], data["portionY"]
    frames = []
    for dots in data["frameDots"]:
        frame = [[0] * width for _ in range(height)]
        for index, value in dots.items():
            i = int(index)
            frame[i // width][i % width] = int(value.split(":")[1])
        frames.append(frame)
    return data, frames
