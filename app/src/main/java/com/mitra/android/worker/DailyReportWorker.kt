package com.mitra.android.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mitra.android.QualityOfLifeCampaign
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class DailyReportWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val qualityOfLifeCampaign: QualityOfLifeCampaign
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val report = qualityOfLifeCampaign.dailyReport()
            qualityOfLifeCampaign.autoClearCache()
            Log.d("DailyReportWorker", "Completed daily maintenance: $report")
            Result.success()
        } catch (t: Throwable) {
            Log.w("DailyReportWorker", "Daily worker failed", t)
            Result.retry()
        }
    }
}
