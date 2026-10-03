package io.github.wxmyyds.coldfront.ui

/** Kept-composed tabs still share the Activity lifecycle; resume alone does not mean visible. */
internal fun isRgbPreviewActive(isPageActive: Boolean, isResumed: Boolean): Boolean =
    isPageActive && isResumed
