package com.custom.app.ui.setting

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.Settings.*
import android.text.TextUtils
import android.text.style.RelativeSizeSpan
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import com.custom.app.BuildConfig
import com.custom.app.R
import com.custom.app.data.DeviceModel
import com.custom.app.service.AutoPauseService
import com.custom.app.service.NotificationHandler
import com.custom.app.util.AlertUtil
import com.custom.app.util.Constant.KEY_DEFAULT_DEVICE
import com.custom.app.util.Constant.KEY_DEVICE_NAME
import com.custom.app.util.Constant.KEY_IGNORE_VOLUME
import com.custom.app.util.Constant.KEY_NOTIFICATION
import com.custom.app.util.Constant.KEY_PERMISSION
import com.custom.app.util.Constant.KEY_REBOOT_ENABLED
import com.custom.app.util.Constant.KEY_RESET
import com.custom.app.util.Constant.KEY_SERVICE_ENABLED
import com.custom.app.util.Constant.KEY_UUID
import com.custom.app.util.Constant.KEY_VERSION
import com.custom.app.util.Constant.NOTIFICATION_STATUS_CHANNEL
import com.custom.app.util.SpannyText
import com.custom.app.util.SpannyTypeface
import dagger.hilt.android.AndroidEntryPoint
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
class SettingFragment : PreferenceFragmentCompat(), SettingManager.ChangeListener {

    @Inject
    lateinit var prefs: SharedPreferences

    @Inject
    lateinit var settings: SettingManager

    @Inject
    lateinit var notifications: NotificationHandler

    private var callback: Callback? = null
    private lateinit var serviceEnabledPreference: SwitchPreference
    private lateinit var rebootEnabledPreference: SwitchPreference
    private lateinit var deviceNamePreference: EditTextPreference
    private lateinit var uuidPreference: EditTextPreference
    private lateinit var ignoreVolumePreference: SwitchPreference
    private lateinit var resetPreference: Preference
    private lateinit var permissionPreference: Preference
    private lateinit var notificationPreference: Preference
    private lateinit var versionPreference: Preference

    override fun onAttach(context: Context) {
        super.onAttach(context)
        callback = context as Callback
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (settings.serviceEnabled()) {
            AutoPauseService.start(requireContext())
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        serviceEnabledPreference = findPreference(KEY_SERVICE_ENABLED)!!
        rebootEnabledPreference = findPreference(KEY_REBOOT_ENABLED)!!
        deviceNamePreference = findPreference(KEY_DEVICE_NAME)!!
        uuidPreference = findPreference(KEY_UUID)!!
        permissionPreference = findPreference(KEY_PERMISSION)!!
        notificationPreference = findPreference(KEY_NOTIFICATION)!!
        ignoreVolumePreference = findPreference(KEY_IGNORE_VOLUME)!!
        resetPreference = findPreference(KEY_RESET)!!
        versionPreference = findPreference(KEY_VERSION)!!

        deviceNamePreference.setOnPreferenceChangeListener { _, newValue ->
            if (!TextUtils.isEmpty(newValue.toString())) {
                settings.deviceName(newValue.toString())
                true
            } else {
                AlertUtil.showToast(requireContext(), "Invalid name!")
                false
            }
        }

        uuidPreference.setOnPreferenceChangeListener { _, newValue ->
            try {
                val uuId = newValue.toString()
                UUID.fromString(uuId)
                settings.uuId(uuId)
                true
            } catch (e: Exception) {
                AlertUtil.showToast(requireContext(), "Invalid format!")
                false
            }
        }

        resetPreference.setOnPreferenceClickListener {
            AlertUtil.showActionAlertDialog(
                requireContext(),
                getString(R.string.msg_reset_default_confirm),
                getString(R.string.btn_cancel), getString(R.string.btn_yes)
            ) { _, _ ->
                AlertUtil.showToast(requireContext(), "Reset to default!")
                AutoPauseService.stop(requireContext())
                settings.clear()
                callback?.onRestartApp()
            }
            true
        }

        permissionPreference.intent =
            Intent(
                ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context?.packageName}")
            ).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

        notificationPreference.intent = if (notifications.areNotificationsEnabled()) {
            Intent(ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                putExtra(EXTRA_CHANNEL_ID, NOTIFICATION_STATUS_CHANNEL)
            }
        } else {
            Intent(ACTION_APP_NOTIFICATION_SETTINGS)
        }.apply {
            putExtra(EXTRA_APP_PACKAGE, BuildConfig.APPLICATION_ID)
        }

        val typeface = Typeface.createFromAsset(context?.assets, "fonts/pacifico.ttf")
        versionPreference.summary = SpannyText()
            .append(getString(R.string.pref_app_version_desc),
                RelativeSizeSpan(0.9f), SpannyTypeface(typeface))
            .append("\n")
            .append("v%s (%s)".format(BuildConfig.VERSION_NAME, BuildConfig.BUILD_TYPE),
                RelativeSizeSpan(0.6f)
            )
    }

    override fun onStart() {
        super.onStart()

        settings.addChangeListener(this)
    }

    override fun onResume() {
        super.onResume()

        if (settings.serviceEnabled()) {
            notifications.updateStatusNotification()
        }
    }

    override fun onStop() {
        super.onStop()

        settings.removeChangeListener(this)
    }

    override fun onSettingsChanged(key: String) {
        when (key) {
            KEY_DEFAULT_DEVICE -> { updateDeviceName(); updateUuid() }
            KEY_SERVICE_ENABLED -> updateServiceState()
            KEY_REBOOT_ENABLED -> updateRebootState()
            KEY_IGNORE_VOLUME -> updateIgnoreVolume()
        }
    }

    override fun onSettingsCleared() {
        updateServiceState()
    }

    private fun updateServiceState() {
        serviceEnabledPreference.isChecked = settings.serviceEnabled()

        if (settings.serviceEnabled()) {
            AutoPauseService.start(requireContext())
        } else {
            AutoPauseService.stop(requireContext())
        }
    }

    private fun updateRebootState() {
        rebootEnabledPreference.isChecked = settings.rebootEnabled()
    }

    private fun updateDeviceName() {
        deviceNamePreference.text = DeviceModel.from(settings.defaultDevice()).model
    }

    private fun updateUuid() {
        val uuid = DeviceModel.from(settings.defaultDevice()).uuid
        if (!TextUtils.isEmpty(uuid)) {
            uuidPreference.text = uuid
        }
    }

    private fun updateIgnoreVolume() {
        ignoreVolumePreference.isChecked = settings.ignoreVolume()
    }

    override fun onDetach() {
        callback = null
        super.onDetach()
    }

    interface Callback {

        fun onRestartApp()

    }
}