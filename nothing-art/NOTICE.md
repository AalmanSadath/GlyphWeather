# Nothing's weather animations

The files in `src/main/resources/nothing-art/` are converted from the widget animations in the
Nothing Weather app, version 3.4.5. The animations are © Nothing Technology Limited.

They are not covered by the GNU GPL or by the additional permission in `LICENSE-EXCEPTION.md`.
Glyph Weather includes them without explicit permission from Nothing, and will remove them if
Nothing asks.

To remove them, delete `src/main/resources/nothing-art/` and rebuild. The app then uses its
original icons and hides the Icons setting.

The loader in `src/main/kotlin/` and the converter in `tools/` are part of Glyph Weather and
are licensed under the GPL like the rest of the code.
