#include <jni.h>

extern "C" JNIEXPORT jboolean JNICALL
Java_com_surajali_showme_MainActivity_nativeCrossedAbove(
        JNIEnv *, jobject, jdouble previous, jdouble current, jdouble target) {
    return (previous < target && current >= target) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_surajali_showme_MainActivity_nativeCrossedBelow(
        JNIEnv *, jobject, jdouble previous, jdouble current, jdouble target) {
    return (previous > target && current <= target) ? JNI_TRUE : JNI_FALSE;
}
