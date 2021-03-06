package uk.co.jakelee.apodwallpaper.scheduling

import android.annotation.SuppressLint
import android.widget.Toast
import com.firebase.jobdispatcher.JobParameters
import com.firebase.jobdispatcher.JobService
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.plugins.RxJavaPlugins
import io.reactivex.schedulers.Schedulers
import timber.log.Timber
import uk.co.jakelee.apodwallpaper.BuildConfig
import uk.co.jakelee.apodwallpaper.R
import uk.co.jakelee.apodwallpaper.api.ApiWrapper.Companion.downloadContent
import uk.co.jakelee.apodwallpaper.helper.PreferenceHelper

// adb shell dumpsys activity service GcmService --endpoints uk.co.jakelee.apodwallpaper
class EndpointCheckJob : JobService() {
    init {
        RxJavaPlugins.setErrorHandler { Timber.e(it.cause, "Uncaught RxJava error") }
    }

    @SuppressLint("CheckResult")
    override fun onStartJob(job: JobParameters): Boolean {
        // If we're testing scheduling, don't actually perform a job
        if (job.tag == TEST_JOB_TAG) {
            Toast.makeText(applicationContext, getString(R.string.test_jobs_success), Toast.LENGTH_LONG).show()
            return false
        }
        // If this is the initial task, also schedule the regular repeating job
        if (job.tag == INITIAL_JOB_TAG) {
            EndpointCheckScheduler(applicationContext).scheduleRepeatingJob()
        }
        val date = EndpointCheckTimingHelper.getLatestDate()
        downloadContent(applicationContext, date, true, false) { jobFinished(job, false) }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({}, {
                Timber.e("Exception during job, try again")
            })
        if (EndpointCheckTimingHelper.isJobBadlyTimed(applicationContext)) {
            fixBadlyTimedJob()
        }
        return true
    }

    private fun fixBadlyTimedJob() {
        val tsw = EndpointCheckScheduler(applicationContext)
        PreferenceHelper(applicationContext).setLongPref(PreferenceHelper.LongPref.last_sync_fix_date,
            System.currentTimeMillis())
        tsw.cancelJob()
        tsw.scheduleJob()
    }

    override fun onStopJob(job: JobParameters?) = true

    companion object {
        const val INITIAL_JOB_TAG = "${BuildConfig.APPLICATION_ID}.initialsync"
        const val JOB_TAG = BuildConfig.APPLICATION_ID
        const val TEST_JOB_TAG = "${BuildConfig.APPLICATION_ID}.test"
    }

}