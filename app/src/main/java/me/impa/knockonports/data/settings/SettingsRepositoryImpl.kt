/*
 * Copyright (c) 2025 Alexander Yaburov
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */

package me.impa.knockonports.data.settings

import android.content.SharedPreferences
import android.os.Build
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import me.impa.knockonports.constants.MIN_IP4_HEADER_SIZE
import me.impa.knockonports.constants.POSTPONE_TIME_START
import me.impa.knockonports.ui.config.ThemeConfig
import me.impa.knockonports.ui.theme.themeMap
import javax.inject.Inject
import javax.inject.Singleton

private const val CFG_APP_THEME = "CFG_APP_THEME"
private const val CFG_DYNAMIC_THEME = "CFG_DYNAMIC_THEME"
private const val CFG_DARK_MODE = "CFG_DARK_MODE"
private const val CFG_CONTRAST = "CFG_CONTRAST"
private const val CFG_CONFIRM_WIDGET = "CFG_CONFIRM_WIDGET"
private const val CFG_DETECT_PUBLIC_IP = "CFG_DETECT_PUBLIC_IP"
private const val CFG_IP_4_SERVICE = "CFG_IP4_SERVICE"
private const val CFG_IP_6_SERVICE = "CFG_IP6_SERVICE"
private const val CFG_IP_6_CUSTOM_SERVICE = "CFG_IP6_CUSTOM_SERVICE"
private const val CFG_IP_4_CUSTOM_SERVICE = "CFG_IP4_CUSTOM_SERVICE"
private const val CFG_FIRST_LAUNCH = "CFG_FIRST_LAUNCH"
private const val CFG_FIRST_LAUNCH_V2 = "CFG_FIRST_LAUNCH_V2"
private const val CFG_KNOCK_COUNT = "CFG_KNOCK_COUNT"
private const val CFG_DO_NOT_ASK_REVIEW = "CFG_DO_NOT_ASK_REVIEW"
private const val CFG_DO_NOT_ASK_BEFORE = "CFG_DO_NOT_ASK_BEFORE"
private const val CFG_DETAILED_LIST_VIEW = "CFG_DETAILED_LIST_VIEW"
private const val CFG_DO_NOT_ASK_NOTIFICATION = "CFG_DO_NOT_ASK_NOTIFICATION"
private const val CFG_IP4_HEADER_SIZE = "CFG_IP4_HEADER_SIZE"
private const val CFG_CUSTOM_IP4_HEADER = "CFG_CUSTOM_IP4_HEADER"

inline fun <reified T : Enum<T>> String.toEnum(default: T): T =
    enumValues<T>().firstOrNull { this.equals(it) } ?: default

/**
 * Implementation of the [SettingsRepository] interface.
 *
 * This class is responsible for managing and persisting application settings using [SharedPreferences].
 * It provides methods to load, save, and update various settings related to the application's behavior,
 * theme, and state.
 *
 * @property sharedPreferences An instance of [SharedPreferences] for persistent storage.
 * @property deviceState An instance of [DeviceState] to access device-related information.
 */
@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val sharedPreferences: SharedPreferences,
    deviceState: DeviceState
) : SettingsRepository {

    private val _appSettings = MutableStateFlow<AppSettings>(AppSettings())

    override val appSettings: StateFlow<AppSettings>
        get() = _appSettings

    private val _themeSettings = MutableStateFlow<ThemeConfig>(ThemeConfig())
    override val themeSettings: StateFlow<ThemeConfig>
        get() = _themeSettings

    private val _appState = MutableStateFlow<AppState>(AppState(
        areShortcutsAvailable = deviceState.areShortcutsAvailable,
        isPlayStoreInstallation = deviceState.isPlayStoreInstallation
    ))
    override val appState: StateFlow<AppState>
        get() = _appState

    init {
        loadSettings()
    }

    private fun loadThemeSettings(): ThemeConfig =
        with(_themeSettings.value) {
            copy(
                useDarkTheme = (sharedPreferences.getString(CFG_DARK_MODE, useDarkTheme.name)
                    ?: useDarkTheme.name).toEnum(useDarkTheme),
                useDynamicColors = sharedPreferences.getBoolean(CFG_DYNAMIC_THEME, useDynamicColors)
                        && (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S),
                customTheme = (sharedPreferences.getString(CFG_APP_THEME, customTheme) ?: customTheme).let {
                    if (themeMap.keys.contains(it)) {
                        it
                    } else {
                        themeMap.keys.first()
                    }
                },
                contrast = (sharedPreferences.getString(CFG_CONTRAST, contrast.name)
                    ?: contrast.name).toEnum(contrast)
            )

        }

    private fun saveThemeSettings() {
        with(_themeSettings.value) {
            sharedPreferences.edit {
                putString(CFG_APP_THEME, customTheme)
                putString(CFG_DARK_MODE, useDarkTheme.name)
                putBoolean(CFG_DYNAMIC_THEME, useDynamicColors)
                putString(CFG_CONTRAST, contrast.name)
            }
        }
    }

    private fun loadAppState() {
        sharedPreferences.getLong(CFG_FIRST_LAUNCH, 0).also {
            if (it == 0L) {
                // First launch
                val currentTime = System.currentTimeMillis()
                sharedPreferences.edit {
                    putLong(CFG_FIRST_LAUNCH, currentTime)
                    putLong(CFG_FIRST_LAUNCH_V2, currentTime)
                    putLong(CFG_DO_NOT_ASK_BEFORE, currentTime + POSTPONE_TIME_START)
                }
            } else {
                sharedPreferences.getLong(CFG_FIRST_LAUNCH_V2, 0).also {
                    if (it == 0L) {
                        // V2 first launch
                        _appState.update { it.copy(isFirstLaunchV2 = true) }
                        sharedPreferences.edit {
                            putLong(CFG_FIRST_LAUNCH_V2, System.currentTimeMillis())
                        }
                    }
                }
            }
        }

        _appState.update {
            it.copy(
                knockCount = sharedPreferences.getLong(CFG_KNOCK_COUNT, 0),
                notificationPermissionRequestDisabled = sharedPreferences.getBoolean(
                    CFG_DO_NOT_ASK_NOTIFICATION,
                    false
                ),
                reviewRequestTimestamp = sharedPreferences.getLong(CFG_DO_NOT_ASK_BEFORE, 0),
                reviewRequestDisabled = sharedPreferences.getBoolean(CFG_DO_NOT_ASK_REVIEW, false)
            )
        }
    }

    private fun loadSettings() {
        // Load app settings from SharedPreferences
        loadAppState()
        _themeSettings.value = loadThemeSettings()
        _appSettings.value = _appSettings.value.copy(
            widgetConfirmation = sharedPreferences.getBoolean(CFG_CONFIRM_WIDGET, false),
            detectPublicIP = sharedPreferences.getBoolean(CFG_DETECT_PUBLIC_IP, false),
            ipv4Service = (sharedPreferences.getString(CFG_IP_4_SERVICE, "") ?: "").let {
                if (Ipv4ProviderMap.keys.contains(it)) {
                    it
                } else {
                    Ipv4ProviderMap.keys.first()
                }
            },
            ipv6Service = (sharedPreferences.getString(CFG_IP_6_SERVICE, "") ?: "").let {
                if (Ipv6ProviderMap.keys.contains(it)) {
                    it
                } else {
                    Ipv6ProviderMap.keys.first()
                }
            },
            customIpv4Service = sharedPreferences.getString(CFG_IP_4_CUSTOM_SERVICE, "") ?: "",
            customIpv6Service = sharedPreferences.getString(CFG_IP_6_CUSTOM_SERVICE, "") ?: "",
            detailedListView = sharedPreferences.getBoolean(CFG_DETAILED_LIST_VIEW, true),
            ip4HeaderSize = sharedPreferences.getInt(CFG_IP4_HEADER_SIZE, MIN_IP4_HEADER_SIZE),
            customIp4Header = sharedPreferences.getBoolean(CFG_CUSTOM_IP4_HEADER, false)
        )
    }

    private fun saveAppSettings() {
        with(_appSettings.value) {
            sharedPreferences.edit {
                putBoolean(CFG_CONFIRM_WIDGET, widgetConfirmation)
                putBoolean(CFG_DETECT_PUBLIC_IP, detectPublicIP)
                putString(CFG_IP_4_SERVICE, ipv4Service)
                putString(CFG_IP_6_SERVICE, ipv6Service)
                putString(CFG_IP_4_CUSTOM_SERVICE, customIpv4Service)
                putString(CFG_IP_6_CUSTOM_SERVICE, customIpv6Service)
                putBoolean(CFG_DETAILED_LIST_VIEW, detailedListView)
                putInt(CFG_IP4_HEADER_SIZE, ip4HeaderSize)
                putBoolean(CFG_CUSTOM_IP4_HEADER, customIp4Header)
            }
        }
    }

    override fun updateAppSettings(newSettings: AppSettings) {
        _appSettings.value = newSettings
        saveAppSettings()
    }

    override fun updateThemeSettings(newSettings: ThemeConfig) {
        _themeSettings.value = newSettings
        saveThemeSettings()
    }

    override fun incrementKnockCount() {
        _appState.updateAndGet { it.copy(knockCount = it.knockCount + 1) }.also {
            sharedPreferences.edit { putLong(CFG_KNOCK_COUNT, it.knockCount) }
        }
    }

    override fun setDoNotAskAboutNotificationsFlag() {
        _appState.updateAndGet { it.copy(notificationPermissionRequestDisabled = true) }.also {
            sharedPreferences.edit { putBoolean(CFG_DO_NOT_ASK_NOTIFICATION, it.notificationPermissionRequestDisabled) }
        }
    }

    override fun postponeReviewRequest(time: Long) {
        _appState.updateAndGet { it.copy(reviewRequestTimestamp = System.currentTimeMillis() + time) }
            .also {
                sharedPreferences.edit { putLong(CFG_DO_NOT_ASK_BEFORE, it.reviewRequestTimestamp) }
            }
    }

    override fun doNotAskForReview() {
        _appState.updateAndGet { it.copy(reviewRequestDisabled = true) }.also {
            sharedPreferences.edit { putBoolean(CFG_DO_NOT_ASK_REVIEW, it.reviewRequestDisabled) }
        }
    }

    override fun clearFirstLaunchV2() {
        _appState.update { it.copy(isFirstLaunchV2 = false) }
    }
}