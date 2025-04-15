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

package me.impa.knockonports

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.core.graphics.drawable.toDrawable
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import me.impa.knockonports.constants.EXTRA_SEQ_ID
import me.impa.knockonports.constants.EXTRA_SOURCE
import me.impa.knockonports.constants.EXTRA_VALUE_SOURCE_SHORTCUT
import me.impa.knockonports.constants.EXTRA_VALUE_SOURCE_WIDGET
import me.impa.knockonports.constants.INVALID_SEQ_ID
import me.impa.knockonports.data.KnocksRepository
import me.impa.knockonports.di.IoDispatcher
import me.impa.knockonports.extension.debounced
import me.impa.knockonports.extension.shortcutId
import me.impa.knockonports.knock.KnockerService
import me.impa.knockonports.ui.theme.KnockOnPortsTheme
import me.impa.knockonports.util.ShortcutManagerWrapper
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class StartKnockingActivity : ComponentActivity() {

    @Inject
    lateinit var repository: KnocksRepository

    @Inject
    @IoDispatcher
    lateinit var ioDispatcher: CoroutineDispatcher

    @Inject
    lateinit var shortcutManagerWrapper: ShortcutManagerWrapper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawable(0.toDrawable())
        // Get sequence id from deeplink (high priority) or extras (low priority)
        val sequenceId = intent.data?.lastPathSegment?.toLongOrNull()
            ?: intent.getLongExtra(EXTRA_SEQ_ID, INVALID_SEQ_ID)
        val source = intent.getStringExtra(EXTRA_SOURCE)

        if (sequenceId == INVALID_SEQ_ID) {
            Timber.e("Invalid sequence id")
            finish()
            return
        }

        setContent {
            val theme by repository.getThemeSettings().collectAsState()
            val settings by repository.getAppSettings().collectAsState()
            KnockOnPortsTheme(config = theme) {
                if (source == EXTRA_VALUE_SOURCE_WIDGET && settings.widgetConfirmation) {
                    var sequenceName by rememberSaveable { mutableStateOf<String?>(null) }
                    LaunchedEffect(sequenceId) {
                        withContext(ioDispatcher) {
                            val name = repository.getSequenceName(sequenceId)
                            if (name == null)
                                finish()
                            else
                                sequenceName = name
                        }
                    }
                    sequenceName?.let {
                        ConfirmationAlert(it) { confirmed ->
                            if (confirmed) {
                                launchSequence(sequenceId)
                            }
                            finish()
                        }
                    }
                } else {
                    LaunchedEffect(true) {
                        if (source == EXTRA_VALUE_SOURCE_SHORTCUT &&
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1
                        ) {
                            (shortcutManagerWrapper as? ShortcutManagerWrapper.Available)
                                ?.instance?.reportShortcutUsed(shortcutId(sequenceId))
                        }
                        launchSequence(sequenceId)
                    }
                }
            }
        }
    }

    @Composable
    private fun ConfirmationAlert(sequenceName: String, onResult: (Boolean) -> Unit = {}) {
        AlertDialog(
            onDismissRequest = { onResult(false) },
            title = { Text(text = stringResource(R.string.title_confirm_knock_alert)) },
            text = {
                Column {
                    Text(text = stringResource(R.string.text_confirm_knock_alert, sequenceName))
                }
            },
            dismissButton = {
                Button(onClick = { onResult(false) }) {
                    Text(text = stringResource(R.string.action_no))
                }
            },
            confirmButton = {
                Button(onClick = debounced({ onResult(true) })) {
                    Text(text = stringResource(R.string.action_yes))
                }
            }
        )
    }

    fun launchSequence(sequenceId: Long) {
        KnockerService.startService(this, sequenceId)
        finish()
    }

}