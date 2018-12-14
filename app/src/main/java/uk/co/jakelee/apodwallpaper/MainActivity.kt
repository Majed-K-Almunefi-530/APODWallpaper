package uk.co.jakelee.apodwallpaper

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.text.format.DateUtils
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_main.*
import timber.log.Timber
import uk.co.jakelee.apodwallpaper.api.ApiClient
import uk.co.jakelee.apodwallpaper.api.ResponseApodProcessed
import uk.co.jakelee.apodwallpaper.helper.FileSystemHelper
import uk.co.jakelee.apodwallpaper.helper.PreferenceHelper
import uk.co.jakelee.apodwallpaper.helper.SettingsHelper
import uk.co.jakelee.apodwallpaper.helper.WallpaperHelper
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    var disposable: Disposable? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        displayLatestSavedApod()
        recheckButton.setOnClickListener {
            getApod(Calendar.getInstance().time)
        }
        fullsizeButton.setOnClickListener {
            startActivity(Intent(this, ImageActivity::class.java))
        }
        if (!PreferenceHelper(this).haveScheduledTask()) {
            JobScheduler.scheduleJob(this)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.top_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_settings -> {
                Toast.makeText(this, "Display some kind of settings...", Toast.LENGTH_SHORT).show()
            }
            R.id.nav_calendar -> {
                Toast.makeText(this, "Display date selector...", Toast.LENGTH_SHORT).show()
            }
        }
        return true
    }

    private fun displayLatestSavedApod() {
        val lastPulled = PreferenceHelper(this).getLastPulledDate()
        val lastChecked = DateUtils.getRelativeTimeSpanString(PreferenceHelper(this).getLastCheckedDate())
        val apodData = PreferenceHelper(this).getApodData(FileSystemHelper(this), lastPulled)
        backgroundImage.setImageBitmap(apodData.image)
        titleBar.text = apodData.title
        descriptionBar.text = apodData.desc
        metadataBar.text = String.format(getString(R.string.last_checked), lastPulled, lastChecked)
    }

    private fun getApod(date: Date) {
        val dateString = SimpleDateFormat("yyyy-MM-dd", Locale.UK).format(date)
        val url = "https://api.nasa.gov/planetary/apod?api_key=${BuildConfig.APOD_API_KEY}&date=$dateString&hd=true"
        disposable = Single
            .fromCallable { ApiClient(url).getApodResponse() }
            .map {
                val bitmap = it.pullRemoteImage()
                if (it.isValid()) {
                    return@map ResponseApodProcessed(it, bitmap)
                }
                throw IOException()
            }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                {
                    val prefHelper = PreferenceHelper(this)
                    prefHelper.updateLastCheckedDate()
                    if (prefHelper.getLastPulledDate() != it.date) {
                        prefHelper.updateLastPulledDate(it.date)
                        prefHelper.saveApodData(it)
                        FileSystemHelper(this).saveImage(it.image!!, it.date)
                        if (SettingsHelper.setWallpaper) {
                            WallpaperHelper(this).updateWallpaper(it.image)
                        }
                        if (SettingsHelper.setLockScreen) {
                            WallpaperHelper(this).updateLockScreen(FileSystemHelper(this).getImage(it.date))
                        }
                        displayLatestSavedApod()
                    }
                },
                { Timber.e(it) }
            )
    }

    override fun onDestroy() {
        super.onDestroy()
        disposable?.dispose()
    }

}
