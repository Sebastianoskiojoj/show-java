/*
 * Show Java - A java/apk decompiler for android
 * Copyright (c) 2018 Niranjan Rajendran
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.njlabs.showjava.activities.decompiler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.animation.Animation
import android.view.animation.LinearInterpolator
import android.view.animation.RotateAnimation
import android.widget.TextView
import com.njlabs.showjava.R
import com.njlabs.showjava.activities.BaseActivity
import com.njlabs.showjava.data.PackageInfo
import kotlinx.android.synthetic.main.activity_decompiler_process.*
import android.content.IntentFilter
import android.widget.Toast
import androidx.lifecycle.Observer
import androidx.work.State
import androidx.work.WorkManager
import androidx.work.WorkStatus
import com.njlabs.showjava.Constants
import com.njlabs.showjava.activities.explorer.navigator.NavigatorActivity
import com.njlabs.showjava.data.SourceInfo
import com.njlabs.showjava.utils.sourceDir
import com.njlabs.showjava.workers.DecompilerWorker
import timber.log.Timber


class DecompilerProcessActivity : BaseActivity() {

    private val statusesMap = mutableMapOf(
        "jar-extraction" to State.ENQUEUED,
        "java-extraction" to State.ENQUEUED,
        "resources-extraction" to State.ENQUEUED
    )

    private lateinit var packageInfo: PackageInfo

    override fun init(savedInstanceState: Bundle?) {
        setupLayout(R.layout.activity_decompiler_process)
        packageInfo = intent.getParcelableExtra("packageInfo")
        val decompilerIndex = intent.getIntExtra("decompilerIndex", 0)

        inputPackageLabel.text = packageInfo.label

        val decompilers = resources.getStringArray(R.array.decompilers)
        val decompilerDescriptions = resources.getStringArray(R.array.decompilerDescriptions)

        decompilerItemCard.findViewById<TextView>(R.id.decompilerName).text = decompilers[decompilerIndex]
        decompilerItemCard.findViewById<TextView>(R.id.decompilerDescription).text = decompilerDescriptions[decompilerIndex]

        setupGears()

        val statusIntentFilter = IntentFilter(Constants.WORKER.ACTION.BROADCAST + packageInfo.name)
        registerReceiver(progressReceiver, statusIntentFilter)

        cancelButton.setOnClickListener {
            DecompilerWorker.cancel(context, packageInfo.name)
            finish()
        }

        WorkManager.getInstance()
            .getStatusesForUniqueWorkLiveData(packageInfo.name)
            .observe(this, Observer<List<WorkStatus>> { statuses ->
                statuses.forEach {
                    statusesMap.keys.forEach { tag ->
                        if (it.tags.contains(tag)) {
                            statusesMap[tag] = it.state
                        }
                    }
                    reconcileDecompilerStatus()
                }
            })
    }

    private fun reconcileDecompilerStatus() {
        val hasFailed = statusesMap.values.any { it == State.FAILED }
        val hasPassed = statusesMap.values.all { it == State.SUCCEEDED }
        Timber.d("[status] [${packageInfo.name}] hasPassed: $hasPassed | hasFailed: $hasFailed")

        if (hasFailed) {
            Toast.makeText(
                context,
                getString(R.string.errorDecompilingApp, packageInfo.label),
                Toast.LENGTH_LONG
            ).show()
            return
        }

        if (hasPassed) {
            val intent = Intent(context, NavigatorActivity::class.java)
            intent.putExtra("selectedApp", SourceInfo.from(sourceDir(packageInfo.name)))
            startActivity(intent)
            finish()
        }
    }

    private fun getGearAnimation(duration: Int = 1, isClockwise: Boolean = true): RotateAnimation {
        val animation = RotateAnimation(
            if (isClockwise) 0.0f else 360.0f,
            if (isClockwise) 360.0f else 0.0f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        )
        animation.repeatCount = Animation.INFINITE
        animation.duration = duration.toLong() * 1500
        animation.interpolator = LinearInterpolator()
        return animation
    }

    private fun setupGears() {
        leftProgressGear.post { leftProgressGear.animation = getGearAnimation(2, true) }
        rightProgressGear.post { rightProgressGear.animation = getGearAnimation(1, false) }
    }

    private val progressReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            intent.getStringExtra(Constants.WORKER.STATUS_KEY)?.let {
                if (it.trim().isNotEmpty()) {
                    statusTitle.text = it
                }
            }
            intent.getStringExtra(Constants.WORKER.STATUS_MESSAGE)?.let {
                statusText.text = it
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(progressReceiver)
    }
}