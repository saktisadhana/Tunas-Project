# Tunas Android design contract

Source of visual truth: Figma `CxprN9kEWf40fE0J5S6c9M`, frames Welcome `13:348`, Home `1:4`, Plants `13:454`, Maintenance `20:740`. Screenshots are in `design/reference/`; original exported vector artwork is in `design/assets/`.

## Design read
Native, mobile plant-care companion for people caring for plants and pairing sensors. Warm cream, olive and sage; bold humanist headings; quiet dashed grid and oversized monstera leaf. ENERGY 2 / RHYTHM 2 / MOTION 1. Motion is limited to native input/progress feedback, not looping decoration.

## Tokens
- Background `#EFEECF`; raised surface `#ECEBD7`.
- Olive `#454D39`; forest `#3B5142`; sage `#A2B595`.
- Merriweather Sans 700/800 headings and measurements, Signika Negative 400/500/700 labels/body.
- 10dp surface radius, 20dp field/control radius, pill-shaped floating tab bar.
- 16dp screen margin, 14–16dp grid gutter; metric cards 104dp high at default font scale.
- Figma artwork/gradient icons retained as local raster assets at 3x, not temporary network URLs. Interactive text, navigation and controls are native Views, never screen images or WebViews.

The attached HTML is a flow inventory only: profile, plant details/addition, rooms, care tasks and Bluetooth pairing. Its Nunito/Nocturne tokens, palette, cards, and iOS device chrome are not used.

## Intentional functional adaptations
The Figma values (88%, sample rooms and sensor counts) are placeholders, not production observations. Start with no saved plants/rooms and unavailable measurements. The library contains starter species, separate from My Plants. Labels in the original vectorized Maintenance panel are rebuilt as accessible native text/controls. Touch targets are at least 48dp; content scrolls above the floating nav, with system/keyboard insets. Larger fonts can grow cards and wrap labels.

Account backend and firmware are not supplied. Profile setup/opening is local-only and visibly described as such; there are no fake social logins, passwords or password resets. Future cloud auth must be implemented before describing this as an authenticated account. Physical watering/nutrition requires a real connected compatible device, an explicit confirmation, bounded duration and acknowledgement; the UI never pretends an offline command ran.
