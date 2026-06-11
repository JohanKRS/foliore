# Android Share Target

The incoming image cover share target is planned for a later milestone.

Future Android components:

- `CoverShareReceiverActivity`
- Intent filter for `Intent.ACTION_SEND` with `image/*`
- Cache copy into `/cache/incoming-covers/`
- Book selection screen reusing the library grid
- `ReplaceBookCoverUseCase`

The first delivery keeps the manifest prepared with FileProvider support but does not expose the cover share target yet.

