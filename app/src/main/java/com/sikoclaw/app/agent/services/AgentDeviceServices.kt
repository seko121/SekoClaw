/* Android adaptation of Kai SMS/notification service controls. Apache-2.0. */
package com.sikoclaw.app.agent.services

import com.sikoclaw.app.utils.KVUtils

object AgentDeviceServices {
    private const val READ_SMS = "AGENT_SERVICE_READ_SMS"
    private const val SEND_SMS = "AGENT_SERVICE_SEND_SMS"
    private const val NOTIFICATIONS = "AGENT_SERVICE_NOTIFICATIONS"
    private const val NOTIFICATION_PACKAGES = "AGENT_SERVICE_NOTIFICATION_PACKAGES"
    fun readSmsEnabled() = KVUtils.getBoolean(READ_SMS, false)
    fun sendSmsEnabled() = KVUtils.getBoolean(SEND_SMS, false)
    fun notificationsEnabled() = KVUtils.getBoolean(NOTIFICATIONS, false)
    fun setReadSmsEnabled(value: Boolean) = KVUtils.putBoolean(READ_SMS, value)
    fun setSendSmsEnabled(value: Boolean) = KVUtils.putBoolean(SEND_SMS, value)
    fun setNotificationsEnabled(value: Boolean) = KVUtils.putBoolean(NOTIFICATIONS, value)
    fun notificationPackages(): Set<String> = KVUtils.getString(NOTIFICATION_PACKAGES, "")
        .split(',').map(String::trim).filter(String::isNotEmpty).toSet()
    fun setNotificationPackages(packages: Set<String>) =
        KVUtils.putString(NOTIFICATION_PACKAGES, packages.sorted().joinToString(","))
    fun notificationPackageAllowed(packageName: String): Boolean {
        val allowed = notificationPackages()
        return allowed.isEmpty() || packageName in allowed
    }
}
