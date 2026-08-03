package com.sikoclaw.app.ui.guide

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.material.button.MaterialButton
import com.sikoclaw.app.AppCapabilityCoordinator
import com.sikoclaw.app.AppRequirement
import com.sikoclaw.app.R
import com.sikoclaw.app.base.BaseActivity
import com.sikoclaw.app.ui.chat.ComposeChatActivity
import com.sikoclaw.app.ui.settings.ProviderManagementActivity
import com.sikoclaw.app.utils.KVUtils

/** First-install setup flow. All permissions are optional and remain editable in Settings. */
class GuideActivity : BaseActivity() {
    private data class Step(val title: Int, val description: Int, val art: Int, val action: Action)
    private enum class Action { NEXT, ACCESSIBILITY, STORAGE, NOTIFICATIONS, BATTERY, PROVIDER, REVIEW, DEPLOY }

    private val steps = listOf(
        Step(R.string.onboarding_welcome_title, R.string.onboarding_welcome_desc, R.drawable.octobot_idle, Action.NEXT),
        Step(R.string.onboarding_accessibility_title, R.string.onboarding_accessibility_desc, R.drawable.octobot_working, Action.ACCESSIBILITY),
        Step(R.string.onboarding_file_title, R.string.onboarding_file_desc, R.drawable.octobot_working, Action.STORAGE),
        Step(R.string.onboarding_notifications_title, R.string.onboarding_notifications_desc, R.drawable.octobot_notifications, Action.NOTIFICATIONS),
        Step(R.string.onboarding_battery_title, R.string.onboarding_battery_desc, R.drawable.octobot_battery, Action.BATTERY),
        Step(R.string.onboarding_provider_title, R.string.onboarding_provider_desc, R.drawable.octobot_add_api, Action.PROVIDER),
        Step(R.string.onboarding_review_title, R.string.onboarding_review_desc, R.drawable.octobot_ready, Action.REVIEW),
        Step(R.string.onboarding_deploy_title, R.string.onboarding_deploy_desc, R.drawable.octobot_ready, Action.DEPLOY),
    )

    private var index = 0
    private lateinit var art: ImageView
    private lateinit var title: TextView
    private lateinit var description: TextView
    private lateinit var progress: TextView
    private lateinit var status: TextView
    private lateinit var action: MaterialButton
    private lateinit var back: MaterialButton

    private val legacyStoragePermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { render() }
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { render() }
    private val providerSetup = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { render() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(5, 20, 49)
        window.navigationBarColor = android.graphics.Color.rgb(5, 20, 49)
        setContentView(R.layout.activity_guide)
        art = findViewById(R.id.ivGuideArt)
        title = findViewById(R.id.tvGuideTitle)
        description = findViewById(R.id.tvGuideDescription)
        progress = findViewById(R.id.tvProgress)
        status = findViewById(R.id.tvGuideStatus)
        action = findViewById(R.id.btnAction)
        back = findViewById(R.id.btnBack)
        index = savedInstanceState?.getInt("step")?.coerceIn(0, steps.lastIndex) ?: 0
        findViewById<View>(R.id.tvSkip).setOnClickListener { next() }
        back.setOnClickListener { if (index > 0) { index--; render() } }
        action.setOnClickListener { perform(steps[index].action) }
        render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("step", index)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        if (::status.isInitialized) render()
    }

    private fun render() {
        val step = steps[index]
        progress.text = "${index + 1} / ${steps.size}"
        title.setText(step.title)
        description.setText(step.description)
        art.setImageResource(step.art)
        (art.drawable as? android.graphics.drawable.AnimatedImageDrawable)?.start()
        // GONE keeps the first-screen Continue button aligned and full width;
        // INVISIBLE previously reserved an empty 88dp slot only on Welcome.
        back.visibility = if (index == 0) View.GONE else View.VISIBLE
        action.setText(
            when (step.action) {
                Action.DEPLOY -> R.string.onboarding_deploy
                Action.PROVIDER -> if (capabilityEnabled(Action.PROVIDER) == true) R.string.guide_start else R.string.onboarding_configure
                Action.ACCESSIBILITY, Action.STORAGE, Action.NOTIFICATIONS, Action.BATTERY -> R.string.onboarding_enable
                else -> R.string.guide_start
            }
        )
        val enabled = capabilityEnabled(step.action)
        status.visibility = if (enabled == null) View.GONE else View.VISIBLE
        status.setText(if (enabled == true && step.action == Action.PROVIDER) R.string.onboarding_connected else if (enabled == true) R.string.onboarding_enabled else R.string.onboarding_not_enabled)
        if (enabled == true) action.setText(R.string.guide_start)
    }

    private fun capabilityEnabled(action: Action): Boolean? = when (action) {
        Action.ACCESSIBILITY -> AppCapabilityCoordinator.snapshot(this).accessibilityState == com.sikoclaw.app.ServiceBindingState.READY
        Action.STORAGE -> AppCapabilityCoordinator.snapshot(this).storageAccessGranted
        Action.NOTIFICATIONS -> AppCapabilityCoordinator.isNotificationPermissionGranted(this)
        Action.BATTERY -> AppCapabilityCoordinator.snapshot(this).batteryOptimizationIgnored
        Action.PROVIDER -> KVUtils.hasLlmConfig()
        else -> null
    }

    private fun perform(type: Action) {
        if (capabilityEnabled(type) == true && type !in listOf(Action.PROVIDER, Action.DEPLOY)) {
            next(); return
        }
        when (type) {
            Action.ACCESSIBILITY -> AppCapabilityCoordinator.openSystemSettings(this, AppRequirement.ACCESSIBILITY)
            Action.STORAGE -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    AppCapabilityCoordinator.openSystemSettings(this, AppRequirement.STORAGE)
                } else {
                    legacyStoragePermission.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE))
                }
            }
            Action.NOTIFICATIONS -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                else next()
            }
            Action.BATTERY -> AppCapabilityCoordinator.openSystemSettings(this, AppRequirement.BATTERY_OPTIMIZATION)
            Action.PROVIDER -> providerSetup.launch(Intent(this, ProviderManagementActivity::class.java))
            Action.DEPLOY -> deploy()
            else -> next()
        }
    }

    private fun next() {
        if (index < steps.lastIndex) { index++; render() } else deploy()
    }

    private fun deploy() {
        KVUtils.markOctoBotDeployed()
        val shouldWake = KVUtils.claimFirstWakeMessage()
        startActivity(Intent(this, ComposeChatActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            if (shouldWake) putExtra(ComposeChatActivity.EXTRA_FIRST_WAKE, true)
        })
        finish()
    }
}
