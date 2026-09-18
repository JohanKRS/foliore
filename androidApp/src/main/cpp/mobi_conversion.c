/* SPDX-License-Identifier: LGPL-3.0-or-later */
#include "mobi_conversion.h"
#include <mobi.h>
#include <stdio.h>
#include <sys/stat.h>

#define INPUT_LIMIT (256UL * 1024 * 1024)
#define TEXT_LIMIT (32UL * 1024 * 1024)
#define ENTRY_LIMIT (32UL * 1024 * 1024)
#define OUTPUT_LIMIT (512UL * 1024 * 1024)
#define ENTRY_COUNT_LIMIT 9998

typedef struct { size_t bytes; size_t entries; int has_opf; } Budget;

static int write_parts(const MOBIPart *part, const char *prefix, const char *directory, Budget *budget) {
    for (; part; part = part->next) {
        if (!part->size) continue;
        if (!part->data || part->size > ENTRY_LIMIT || part->size > OUTPUT_LIMIT - budget->bytes ||
            ++budget->entries > ENTRY_COUNT_LIMIT) return CONVERSION_TOO_LARGE;
        budget->bytes += part->size;
        const MOBIFileMeta meta = mobi_get_filemeta_by_type(part->type);
        char path[4096];
        int length;
        if (part->type == T_OPF) {
            if (budget->has_opf) return CONVERSION_INVALID;
            budget->has_opf = 1;
            length = snprintf(path, sizeof(path), "%s/content.opf", directory);
        } else {
            length = snprintf(path, sizeof(path), "%s/%s%05zu.%s", directory, prefix, part->uid, meta.extension);
        }
        if (length < 0 || (size_t) length >= sizeof(path)) return CONVERSION_IO;
        FILE *output = fopen(path, "wbx");
        if (!output) return CONVERSION_IO;
        const size_t written = fwrite(part->data, 1, part->size, output);
        const int closed = fclose(output);
        if (written != part->size || closed != 0) return CONVERSION_IO;
    }
    return CONVERSION_OK;
}

int foliore_extract_mobi(const char *source, const char *destination) {
    struct stat info;
    if (stat(source, &info) != 0 || !S_ISREG(info.st_mode) || info.st_size <= 0) return CONVERSION_INVALID;
    if ((unsigned long long) info.st_size > INPUT_LIMIT) return CONVERSION_TOO_LARGE;
    FILE *input = fopen(source, "rb");
    if (!input) return CONVERSION_IO;
    MOBIData *mobi = mobi_init();
    if (!mobi) { fclose(input); return CONVERSION_IO; }
    int result = CONVERSION_INVALID;
    MOBIRawml *rawml = NULL;
    const MOBI_RET loaded = mobi_load_file(mobi, input);
    fclose(input);
    if (loaded != MOBI_SUCCESS) goto cleanup;
    if (mobi_is_encrypted(mobi)) { result = CONVERSION_DRM; goto cleanup; }
    if (mobi_is_replica(mobi)) { result = CONVERSION_UNSUPPORTED; goto cleanup; }
    if (mobi_get_text_maxsize(mobi) > TEXT_LIMIT) { result = CONVERSION_TOO_LARGE; goto cleanup; }
    rawml = mobi_init_rawml(mobi);
    if (!rawml || mobi_parse_rawml(rawml, mobi) != MOBI_SUCCESS || !rawml->markup) goto cleanup;
    Budget budget = {0};
    result = write_parts(rawml->markup, "part", destination, &budget);
    if (result != CONVERSION_OK) goto cleanup;
    /* The first flow is the unsplit raw document, already represented by markup. */
    result = write_parts(rawml->flow ? rawml->flow->next : NULL, "flow", destination, &budget);
    if (result != CONVERSION_OK) goto cleanup;
    result = write_parts(rawml->resources, "resource", destination, &budget);
    if (result == CONVERSION_OK && !budget.has_opf) result = CONVERSION_INVALID;
cleanup:
    if (rawml) mobi_free_rawml(rawml);
    mobi_free(mobi);
    return result;
}
