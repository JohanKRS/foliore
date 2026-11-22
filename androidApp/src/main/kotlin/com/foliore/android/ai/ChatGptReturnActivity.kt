package com.foliore.android.ai

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import com.foliore.android.MainActivity

/** Brings the existing Foliore task forward from the sign-in page; it carries no data. */
class ChatGptReturnActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(
            Intent.makeMainActivity(ComponentName(this, MainActivity::class.java))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        finish()
    }
}
