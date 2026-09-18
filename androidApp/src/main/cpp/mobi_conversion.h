/* SPDX-License-Identifier: LGPL-3.0-or-later */
#ifndef FOLIORE_MOBI_CONVERSION_H
#define FOLIORE_MOBI_CONVERSION_H
enum { CONVERSION_OK, CONVERSION_INVALID, CONVERSION_DRM, CONVERSION_UNSUPPORTED, CONVERSION_TOO_LARGE, CONVERSION_IO };
/* destination must be a new, empty, private directory. Filenames are generated here. */
int foliore_extract_mobi(const char *source, const char *destination);
#endif
