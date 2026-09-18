# Public domain EPUB sources

Reviewed on 2026-09-18. Foliore links to established sources with EPUB downloads for public domain works. Each destination opens in the user’s browser. We do not embed pages, scrape catalogues, bundle their books or artwork, or imply endorsement.

## Sources and permissions

| Source | EPUB availability and destination | Linking permission |
| --- | --- | --- |
| Project Gutenberg | The homepage in English; the official language index for the other app languages. Book landing pages offer EPUB downloads. | Its [linking guidelines](https://www.gutenberg.org/policy/linking.html) allow ordinary page links without permission. Link to landing pages, never directly to downloadable ebook files. |
| Wikisource | Only the English [Ready for export](https://en.wikisource.org/wiki/Category:Ready_for_export) and French [Bon pour export](https://fr.wikisource.org/wiki/Cat%C3%A9gorie:Bon_pour_export) selections. | Section 3.6 of the [Wikimedia Foundation Trademark Policy](https://foundation.wikimedia.org/wiki/Policy:Wikimedia_Foundation_Trademark_Policy#3.6_Refer_to_Wikimedia_sites_(nominative_use)) allows plain names to refer and link to Wikimedia sites. |
| Ebooks libres et gratuits | <https://www.ebooksgratuits.com/>. Its catalogue provides EPUB download links alongside the other available formats. | Its edition notices explicitly welcome links to the site, including the [May 2025 notice in Les Méfaits du tabac](https://www.ebooksgratuits.com/html/tchekhov_les_mefaits_du_tabac.html). The separate limits on reuse of its editions do not describe ordinary links. |
| Elejandría | <https://www.elejandria.com/>. Its book pages offer EPUB downloads; for example, [Jane Eyre](https://www.elejandria.com/libro/jane-eyre/bronte-charlotte/2383). | Section 8.1 of its [legal notice](https://www.elejandria.com/aviso-legal) specifies conditions for inbound links. Use only the homepage: deep links, framing and image links require prior authorization. The name appears only as part of the hyperlink. Do not reuse its editions, covers or descriptions commercially. |
| Liber Liber | <https://liberliber.it/>. The Progetto Manuzio collection includes free EPUB editions. | Its [Create a link page](https://liberliber.it/aiuta/crea-un-link/) invites links and supplies this homepage address, also linked from its [support page](https://liberliber.it/aiuta/). |

Ebooks libres et gratuits is referenced in a [Bibliothèque nationale de France bibliography](https://www.bnf.fr/sites/default/files/2018-11/biblio%20boccace.pdf). Elejandría is included in the [Instituto Profesional IPG library’s recommended download sources](https://www.ipg.cl/wp-content/uploads/2023/09/PDF_Sitios_web_descarga_libros.pdf). These provide institutional context in addition to the projects’ own descriptions and active catalogues.

These are sources of EPUBs, not a promise that every item in every collection has only that format. Some also offer PDF or openly licensed works. Copyright status varies by jurisdiction. The app links to discovery pages rather than initiating a download.

## Why Wikisource is restricted

Wikisource can export transcribed works to EPUB; it is not simply a PDF archive. However, the [offline reading help](https://en.wikisource.org/wiki/Help:Reading_offline) warns that the general collection includes incomplete texts and works that do not export well. The two selected categories identify works checked for ebook export. Open a work, then choose its EPUB download.

The generic homepages have been removed in every language. Only English and French editions with verified export-ready selections are included. A download button alone is not sufficient grounds to add another edition; verify a useful collection of complete works prepared for EPUB first. No unsupported URL parameters are presented as format filters.

## Language mapping

| App language | Project Gutenberg | Additional sources |
| --- | --- | --- |
| Portuguese | <https://www.gutenberg.org/browse/languages/pt> | — |
| English | <https://www.gutenberg.org/> | Wikisource: Ready for export |
| Spanish | <https://www.gutenberg.org/browse/languages/es> | Elejandría |
| French | <https://www.gutenberg.org/browse/languages/fr> | Ebooks libres et gratuits; Wikisource: Bon pour export |
| German | <https://www.gutenberg.org/browse/languages/de> | — |
| Italian | <https://www.gutenberg.org/browse/languages/it> | Liber Liber |
| Japanese | <https://www.gutenberg.org/browse/languages/ja> | — |
| Simplified Chinese | <https://www.gutenberg.org/browse/languages/zh> | — |

The app’s resolved resources determine the language, including the Portuguese fallback for unsupported system languages. English keeps Gutenberg’s main discovery page rather than its very large alphabetical English index.

## Placement and copy

- Empty library: below the import and sync setup actions, including on first launch.
- Nonempty library: at the end of Settings, below the existing footer links.
- Empty search or filtered results do not count as an empty library.

The shared component uses text buttons with an external-link icon and wraps on narrow screens. The Portuguese invitation is “Toda biblioteca começa com um livro” / “Explore livros de domínio público em EPUB e escolha o primeiro.” Settings uses “Encontre sua próxima leitura” / “Encontre livros de domínio público em EPUB.” Both versions are localized in all eight languages.

## Verification

The initial placement was checked in a disposable Android 16 emulator with empty and populated libraries, language changes and 150% font size. The EPUB-focused revision passed a debug build, Android lint (no errors or warnings in the changed source files), resource checks for all eight languages and HTTP checks for all 13 destinations. Emulator checks covered the Portuguese, Spanish and French selections, opening external links, and the French layout at 150% font size on a narrow screen. Local evidence is under `.codex-artifacts/public-domain/` and `.codex-artifacts/epub-sources/` (not committed).
