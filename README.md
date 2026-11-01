# Glyph Weather

A weather Glyph Toy for the Nothing Phone (3). It shows the weather on the 25×25 Glyph Matrix
on the back of the phone, across four pages:

- **Now**: a weather icon and the current temperature
- **Animation**: a full-size animated weather icon
- **High / low**: today's high above, low below
- **Rain**: chance of rain for each of the next 12 hours

Long-press the Glyph Button to move to the next page.

Weather data comes from [Open-Meteo](https://open-meteo.com/), which needs no API key and
publishes its data under CC BY 4.0.

The weather icons are Nothing's own: the widget animations from the Nothing Weather app,
converted for the 25×25 matrix. The app can switch to the original pixel-art icons instead,
which are drawn in code in the `icons` module.

## Using it

1. Open **Glyph Weather** and choose your city.
2. Tap **Add to Glyph Toys** and add **Weather** to your toys.
3. Press the Glyph Button until Weather is selected.

In the app you can also switch between °C and °F, choose Nothing's or the original icons,
reorder or turn off pages, and turn animation off, separately for normal use and for the
always-on display (set in Settings → Glyph Interface → Flip to Glyph → Always-on Glyph Toy).

With Nothing's icons, the animation plays once when the page appears or the screen turns on,
then stays on its last frame. At 40 °C and above the Animation page shows Nothing's extreme heat
animation instead of the condition, and at −10 °C and below the extreme cold one.

The forecast refreshes about every 30 minutes. The only data sent anywhere is the city name
you search for and your chosen city's coordinates, both to Open-Meteo.

## Where Nothing's animations come from

The Nothing Weather app (version 3.4.5) keeps its widget animations in `res/raw/` as JSON files,
such as `widget_clearday_initial.json`. Each file is a grid of dots, 24 to 32 dots wide, and each
frame lists the lit dots as `"radius:brightness"`. Every dot has the same radius, so the picture
is in the brightness, 0 to 255. Frames are 16 ms apart. An animation moves for 0.7 seconds (clear
night) to 3.3 seconds (snow), then ends on a still image.

`nothing-art/tools/convert.py` converts 13 of them into the files in
`nothing-art/src/main/resources/nothing-art/`:

- Each animation is centred on its last frame. Earlier frames reach further out (rain falls in
  from above), and centring on all frames put some icons 1 to 2 LEDs off centre.
- `full/` holds the Animation page versions, scaled 1.65× to 2.26× with area averaging so each
  fills the round matrix without clipping any frame.
- `small/` holds the Now page versions: one dot per LED, in the 12×12 square above the
  temperature.
- Every 6 source frames are averaged into one 96 ms frame. On a Phone (3) this looked better
  than the same conversion at 30 fps.
- Leading blank frames are dropped, and brightness is scaled so the brightest dot is 255.

Drizzle and rain both use `rainy`. The app doesn't use `cloudy`, `hazysunshine` or
`hazynight`.

To convert them again from the app on a connected phone:

```sh
adb shell pm path com.nothing.weather     # prints the path of base.apk
adb pull <path of base.apk> nothing-weather.apk
unzip nothing-weather.apk 'res/raw/*_initial.json' -d nothing-weather
python3 nothing-art/tools/convert.py nothing-weather/res/raw nothing-art/src/main/resources/nothing-art
```

## Verifying a download

APKs on the [Releases](https://github.com/AalmanSadath/GlyphWeather/releases) page are built by
GitHub Actions and signed with a key whose SHA-256 certificate fingerprint is:

```
4363d984708d3e0c12d32d17ce01be637031dbe74102af2a8a147c23eee91913
```

Check a download against it with `apksigner` from the Android SDK build tools:

```sh
apksigner verify --print-certs GlyphWeather-v0.1.0.apk
```

Each release also has a `.sha256` file; `sha256sum -c GlyphWeather-v0.1.0.apk.sha256` checks
the APK was not corrupted in transit.

## Building

Requirements: JDK 17 or newer and the Android SDK (platform 36).

```sh
./gradlew :app:assembleDebug
```

The first build downloads Nothing's Glyph Matrix SDK into `app/libs/` from Nothing's GitHub
repository, pinned to a known commit and checked against a SHA-256 hash. The SDK's license
does not allow redistribution, so it is not committed here.

### Tests

```sh
./gradlew :icons:test :weather:test :nothing-art:test
```

`OPEN_METEO_LIVE=1 ./gradlew :weather:test` also runs one test against the real Open-Meteo
API.

### Previewing icons and pages

```sh
./gradlew :icons:preview
```

renders every icon and page as animated GIFs and contact sheets into
`icons/build/icon-preview/`.

Debug builds also include an icon tester: an app ("Glyph Weather icon test") that shows any
icon or page on the matrix, and a Glyph Toy ("[Debug] Icon tester") that steps through them
with a long press. Neither is in release builds.

## Project layout

| Module | What's in it |
|---|---|
| `icons` | Pure Kotlin: the LED mask, icons, pixel font and page layouts. No Android dependencies, so it runs in JVM tests and the preview renderer. |
| `weather` | Pure Kotlin: the Open-Meteo client, weather-code mapping and page rendering. |
| `nothing-art` | Nothing's converted weather animations, the code that loads them, and the converter script. The animations are not under the GPL; see `nothing-art/NOTICE.md`. |
| `app` | The Android app: settings screen, Glyph Toy service, background refresh, and the connection to the Glyph Matrix. |

## License

GPL-3.0 (see `LICENSE`), with an additional permission to link against Nothing's proprietary
Glyph Matrix SDK (see `LICENSE-EXCEPTION.md`).

The weather animations in `nothing-art/src/main/resources/nothing-art/` are © Nothing
Technology Limited and are not covered by the GPL. They are included without Nothing's explicit
permission and will be removed if Nothing asks. Deleting that folder leaves the app on its
original icons. See `nothing-art/NOTICE.md`.
