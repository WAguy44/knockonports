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

package me.impa.knockonports.util

import android.content.Context
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.os.Build
import androidx.annotation.RequiresApi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.distinctUntilChanged
import me.impa.knockonports.constants.EXTRA_SEQ_ID
import me.impa.knockonports.constants.INVALID_SEQ_ID
import me.impa.knockonports.data.KnocksRepository
import me.impa.knockonports.data.db.entity.Sequence
import me.impa.knockonports.extension.getShortcutInfo
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Observes changes in the list of sequences and updates dynamic and pinned shortcuts accordingly.
 *
 * This class is responsible for ensuring that the system's shortcuts (both dynamic and pinned)
 * reflect the current state of user-defined sequences.  It handles adding, removing, enabling,
 * disabling, and updating shortcuts based on changes to the sequence data.  It gracefully
 * handles situations where the ShortcutManager is unavailable or the device's API level is
 * insufficient.
 *
 * @property context The application context.
 * @property repository A [KnocksRepository] instance, used to retrieve the list of sequences.
 */
@Singleton
class ShortcutWatcher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: KnocksRepository
) {
    private val shortcutManager by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            context.getSystemService(ShortcutManager::class.java)
        } else {
            null
        }
    }

    @RequiresApi(Build.VERSION_CODES.N_MR1)
    suspend fun startObserving() {
        shortcutManager?.let { manager ->
            repository.getSequences().distinctUntilChanged().collect {
                validateShortcuts(manager, it)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.N_MR1)
    private fun validateShortcuts(manager: ShortcutManager, sequences: List<Sequence>) {
        shortcutManager ?: return
        Timber.d("Validating shortcuts (${sequences.size})")
        if (sequences.isEmpty()) {
            manager.removeAllDynamicShortcuts()
        } else {
            manager.dynamicShortcuts = sequences.filter { !it.name.isNullOrBlank() }
                .take(manager.maxShortcutCountPerActivity).map { it.getShortcutInfo(context) }
        }
        Timber.d("Shortcuts validated, count: ${manager.dynamicShortcuts.size}")

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !manager.isRequestPinShortcutSupported)
            return

        Timber.d("Validating pinned shortcuts")

        val pinnedShortcuts = manager.pinnedShortcuts
        val enableShortcuts = mutableListOf<String>()
        val disableShortcuts = mutableListOf<String>()
        val updateShortcuts = mutableListOf<ShortcutInfo>()

        for (shortcut in pinnedShortcuts) {
            val action = checkShortcut(shortcut, sequences)
            when (action) {
                is Action.Enable -> enableShortcuts.add(shortcut.id)
                is Action.Disable -> disableShortcuts.add(shortcut.id)
                is Action.Update -> updateShortcuts.add(action.info)
                is Action.Skip -> {}
            }
        }
        updateShortcuts.takeIf { it.isNotEmpty() }?.let { manager.updateShortcuts(it) }
        enableShortcuts.takeIf { it.isNotEmpty() }?.let { manager.enableShortcuts(it) }
        disableShortcuts.takeIf { it.isNotEmpty() }?.let { manager.disableShortcuts(it) }
    }

    @Suppress("ReturnCount")
    @RequiresApi(Build.VERSION_CODES.N_MR1)
    private fun checkShortcut(shortcut: ShortcutInfo, sequences: List<Sequence>): Action {
        val id = shortcut.intent?.getLongExtra(EXTRA_SEQ_ID, INVALID_SEQ_ID) ?: return Action.Skip
        val shortcutSequence = sequences.find { it.id == id }
        if (shortcutSequence != null) {
            if (!shortcut.isEnabled && !shortcutSequence.name.isNullOrEmpty())
                return Action.Enable
            if (shortcutSequence.name != shortcut.shortLabel) {
                return if (shortcutSequence.name.isNullOrEmpty())
                    Action.Disable
                else
                    Action.Update(shortcutSequence.getShortcutInfo(context, isAuto = false))
            }
        } else {
            if (shortcut.isEnabled) return Action.Disable
        }

        return Action.Skip
    }


    private sealed interface Action {
        object Enable : Action
        object Disable : Action
        class Update(val info: ShortcutInfo) : Action
        object Skip : Action

    }
}