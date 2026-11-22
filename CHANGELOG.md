# Changelog

All notable changes to Foliore are listed here, newest first. Each version matches a [GitHub Release](https://github.com/JohanKRS/Foliore/releases) with a signed APK. The release process is described in [docs/RELEASING.md](docs/RELEASING.md).

After 1.0.0, each entry groups its changes under **Added**, **Changed**, **Fixed** and **Removed**, leaving out empty groups.

## [1.0.2] - 2026-11-22

### Changed
- **Book page.** Replace cover and Generate or Find covers moved from under the cover to the top of the Metadata section. The page now reads: cover, title, About this book, then Metadata.
- The title under the cover also shows the series and its number when the book has one.

### Fixed
- **AI preview with ChatGPT** failed with "Empty metadata response". The app now reads the answer as it streams in, so the preview fills in. Find covers with ChatGPT gets the same fix.
- The cover buttons no longer cut off their labels with large text. When both don't fit side by side, they stack.

## [1.0.1] - 2026-11-22

### Changed
- **Auto-fill with AI.** With ChatGPT or an API key set up, the AI tab opens first. The preview sits at the top, with Generate or Apply right below it. Edit prompt and the note on what is sent to OpenAI moved under the button, and the note names your ChatGPT plan instead of API charges when you use the plan.
- **Phone bottom bar.** The Read, Export and Kindle buttons line up with the book page, and Library, Sync and Settings sit centred under them.
- **Book page order.** Phones show the cover and title, then About this book, the details and finally sync.

### Fixed
- **Generate preview did nothing** while an open-sources search or a sync was running. It now always starts, shows the time so far, and shows any error inside the dialog.
- The auto-fill buttons no longer get squeezed into a column of letters on narrow screens or with large text, and "Open sources" is no longer cut off.
- Read/Continue keeps its colour and label while you leave a book.

### Removed
- The Support link in Settings. For help, open an issue in the project repository (About → Source code).

## [1.0.0] - 2026-11-22

First public release. It lists the features only, since there is no earlier public version to compare with.

### Features
- **Your EPUB library.** Import the EPUBs you already have, or convert DRM-free MOBI and AZW3 files to EPUB on the device. The original files are kept.
- **Book details and covers.** Edit title, subtitle, authors, series, language, publisher, description and cover. Find metadata and covers in open catalogs (Open Library, Internet Archive and the BnF).
- **Export and Send to Kindle.** Export prepared EPUB copies, share them, or send them to Kindle with Gmail or your own SMTP server.
- **Built-in reader.** Pagination, search, bookmarks and typography controls. Reading progress shows on the covers, with a "Recently read" sort.
- **Optional sync.** Keep the library on the device, or sync it through a hidden Google Drive app folder or a folder you choose.
- **Optional AI.** Sign in with ChatGPT (Plus or Pro) to fill in synopses and details and to find existing covers. Add an OpenAI API key to also generate new covers.
- **Your style.** Light and dark themes, four app icons and eight languages.
- **Free and open source** under the GPL-3.0. No ads, purchases or book limits, and no account required.

[1.0.2]: https://github.com/JohanKRS/Foliore/releases/tag/v1.0.2
[1.0.1]: https://github.com/JohanKRS/Foliore/releases/tag/v1.0.1
[1.0.0]: https://github.com/JohanKRS/Foliore/releases/tag/v1.0.0
