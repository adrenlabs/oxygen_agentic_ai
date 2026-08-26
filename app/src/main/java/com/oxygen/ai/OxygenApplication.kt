package com.oxygen.ai

import android.app.Application
import com.oxygen.ai.core.logging.OxygenLog
import com.oxygen.ai.di.OxygenGraph
import com.oxygen.ai.work.OxygenWork
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class OxygenApplication : Application() {
    lateinit var graph: OxygenGraph
        private set

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        graph = OxygenGraph(this)
        OxygenLog.i("app", "OXYGEN AI starting")
        OxygenWork.schedule(this)
        appScope.launch { graph.start() }
    }

    override fun onTerminate() {
        graph.shutdown()
        super.onTerminate()
    }
}
