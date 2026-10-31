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

All icons are original pixel art, drawn in code in the `icons` module.

## Using it

1. Open **Glyph Weather** and choose your city.
2. Tap **Add to Glyph Toys** and add **Weather** to your toys.
3. Press the Glyph Button until Weather is selected.

In the app you can also switch between °C and °F, reorder or turn off pages, and turn
animation off, separately for normal use and for the always-on display (set in Settings →
Glyph Interface → Flip to Glyph → Always-on Glyph Toy).

The forecast refreshes about every 30 minutes. The only data sent anywhere is the city name
you search for and your chosen city's coordinates, both to Open-Meteo.

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
./gradlew :icons:test :weather:test
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
| `app` | The Android app: settings screen, Glyph Toy service, background refresh, and the connection to the Glyph Matrix. |

## License

GPL-3.0 (see `LICENSE`), with an additional permission to link against Nothing's proprietary
Glyph Matrix SDK (see `LICENSE-EXCEPTION.md`).
