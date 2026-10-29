# Alarm sounds

The alarm sounds in `src/main/res/raw/g7_*.m4a` come from Trio, the open source app for people with
diabetes on iOS.

- Source: [nightscout/Trio pull request 1645](https://github.com/nightscout/Trio/pull/1645),
  "Rework Trio Alarm Sounds: Add Urgency Levels and Voice Alarms", merged on 2026-10-19
  (commit `cd6ad0706db1e7a546360d4631f75b24a402ba58`).
- Brought into Trio by Deniz Cengiz ([dnzxy](https://github.com/dnzxy)).
- The sounds were designed for Trio by a sound designer who is a diabetes dad and Trio user, and
  who wants to stay anonymous.
- The picker, with one row per style and an urgency slider, follows the one in the same pull request.

Thank you to both of them.

## What we took

- The Acoustic and Electronic sets, each at three urgency levels (mild, medium, harsh), for these
  Trio alarms: urgent low (`ulg`), low (`lg`), low soon (`lgs`), high (`hg`), device critical (`dcr`)
  and device time-sensitive (`dts`).
- Eight extra tones from the same pull request: glass bell, harp, meow, saw wave, hard saw wave,
  sine wave, open can and open wrapper.

Not taken: the carbs required set (we have no such alarm), the spoken alarms, and the older Trio
tones that were already there before that pull request.

Our alarms that have no set of their own share one, as Trio does for its normal device alerts: fall
rate uses low soon, rise rate uses high, and signal loss, sensor issue and sensor end use device
time-sensitive.

## What we changed

- Converted from CAF (16-bit PCM) to AAC at 96 kbit/s in an `.m4a` file, because Android does not
  play CAF. Made with `afconvert -f m4af -d aac -b 96000` on macOS.
- Renamed to `g7_<style>_<alarm>_<level>.m4a` and `g7_tone_<name>.m4a`.

Nothing else was changed.

## Licence

Trio is under the MIT licence, and the sounds are part of Trio
([ACKNOWLEDGEMENTS.md](https://github.com/nightscout/Trio/blob/dev/ACKNOWLEDGEMENTS.md)):

```
MIT License

Copyright (c) 2021 Ivan Valkou

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
