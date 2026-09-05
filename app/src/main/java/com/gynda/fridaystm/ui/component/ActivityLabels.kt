package com.gynda.fridaystm.ui.component

import androidx.annotation.StringRes
import com.gynda.fridaystm.R
import com.gynda.fridaystm.domain.Activity

/**
 * Maps a pure [Activity] domain value to its user-facing label resource.
 *
 * Display text is a UI concern, so the mapping lives here (not in `domain/`) and
 * resolves through `stringResource` at the call site — keeping the enum free of
 * presentation strings and letting the label be localized.
 */
@StringRes
fun activityLabelRes(activity: Activity): Int = when (activity) {
    Activity.TALIM -> R.string.activity_talim
    Activity.LARKAM -> R.string.activity_larkam
    Activity.SENAM -> R.string.activity_senam
}
