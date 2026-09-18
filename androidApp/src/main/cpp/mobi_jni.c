/* SPDX-License-Identifier: LGPL-3.0-or-later */
#include <jni.h>
#include "mobi_conversion.h"

JNIEXPORT jint JNICALL Java_com_foliore_android_storage_MobiNative_extract(
    JNIEnv *env, jobject self, jstring source, jstring destination) {
    (void) self;
    const char *source_path = (*env)->GetStringUTFChars(env, source, NULL);
    if (!source_path) return CONVERSION_IO;
    const char *destination_path = (*env)->GetStringUTFChars(env, destination, NULL);
    if (!destination_path) {
        (*env)->ReleaseStringUTFChars(env, source, source_path);
        return CONVERSION_IO;
    }
    const int result = foliore_extract_mobi(source_path, destination_path);
    (*env)->ReleaseStringUTFChars(env, source, source_path);
    (*env)->ReleaseStringUTFChars(env, destination, destination_path);
    return result;
}
