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

package me.impa.knockonports.data

import android.content.Context
import androidx.glance.appwidget.updateAll
import dagger.hilt.EntryPoint
import dagger.hilt.EntryPoints
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.distinctUntilChanged
import me.impa.knockonports.data.db.dao.SequenceDao
import me.impa.knockonports.widget.KnocksWidget
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KnocksWidgetRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sequenceDao: SequenceDao
) {

    suspend fun updateWidget() {
        KnocksWidget().updateAll(context)
    }

    fun getSequences() = sequenceDao.findAllSequences().distinctUntilChanged()

}