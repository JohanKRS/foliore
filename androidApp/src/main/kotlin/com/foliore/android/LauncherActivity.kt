package com.foliore.android

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle

/** A short-lived launcher entry keeps the running task independent of icon aliases.
 * Otherwise Android finishes the visible activity when its launcher alias is disabled,
 * even with PackageManager.DONT_KILL_APP.
 */
class LauncherActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(
            Intent.makeMainActivity(ComponentName(this, MainActivity::class.java))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED),
        )
        finish()
    }
}
