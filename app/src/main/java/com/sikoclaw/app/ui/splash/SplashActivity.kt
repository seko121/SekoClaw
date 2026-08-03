// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package com.sikoclaw.app.ui.splash

import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import com.sikoclaw.app.R
import com.sikoclaw.app.base.BaseActivity
import com.sikoclaw.app.ui.chat.ComposeChatActivity
import com.sikoclaw.app.ui.guide.GuideActivity
import com.sikoclaw.app.utils.KVUtils

/**
 * Splash screen - always navigates to the home screen; LLM does not need to be configured first, it can be set up in Settings
 */
class SplashActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        findViewById<android.widget.ImageView>(R.id.ivLogo)?.let {
            (it.drawable as? android.graphics.drawable.AnimatedImageDrawable)?.start()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { /* Back press disabled on splash screen */ }
        })

        val destination = if (KVUtils.isOctoBotDeployed()) ComposeChatActivity::class.java else GuideActivity::class.java
        val intent = Intent(this, destination)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        // Forward debug task extra
        getIntent()?.getStringExtra("task")?.let { intent.putExtra("task", it) }
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            startActivity(intent)
            finish()
        }, 450L)
    }
}
