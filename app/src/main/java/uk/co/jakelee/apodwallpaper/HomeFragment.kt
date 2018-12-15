package uk.co.jakelee.apodwallpaper

import android.app.DatePickerDialog
import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.fragment_main.*
import timber.log.Timber
import uk.co.jakelee.apodwallpaper.helper.FileSystemHelper
import uk.co.jakelee.apodwallpaper.helper.PreferenceHelper



class HomeFragment : Fragment() {
    var disposable: Disposable? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_main, container, false)
    }

    val dateSetListener = DatePickerDialog.OnDateSetListener { _, year, month, day ->
        resetApod()
        getApod("$year-$month-$day", false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as AppCompatActivity).supportActionBar!!.title = getString(R.string.app_name)
        resetApod()
        displayApod(PreferenceHelper(activity!!).getLastPulledDate())
    }

    private fun setUpFullscreenButton(title: String, dateString: String){
        fullscreenButton.setOnClickListener {
            val imageFile = FileSystemHelper(activity!!).getImage(dateString)
            val bundle = Bundle().apply {
                putString("image", imageFile.path)
                putString("title", title)
            }
            val fragment = ImageFragment().apply { arguments = bundle }
            activity!!.supportFragmentManager.beginTransaction()
                .setCustomAnimations(R.anim.enter_from_right, R.anim.exit_to_left, R.anim.enter_from_left, R.anim.exit_to_right)
                .replace(R.id.mainFrame, fragment, "image_fragment")
                .addToBackStack(null)
                .commit()
        }
        fullscreenButton.visibility = View.VISIBLE
    }

    fun getApod(dateString: String, pullingLatest: Boolean) {
        if (PreferenceHelper(activity!!).doesDataExist(dateString)) {
            displayApod(dateString)
        } else {
            disposable = JobScheduler.downloadApod(activity!!, dateString, pullingLatest)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { displayApod(dateString) },
                    { Timber.e(it) }
                )
        }
    }

    private fun resetApod() {
        backgroundImage.setImageResource(R.color.colorPrimary)
        titleBar.text = activity!!.getString(R.string.loading_message)
        descriptionBar.visibility = View.GONE
        metadataBar.visibility = View.GONE
        fullscreenButton.visibility = View.GONE
    }

    private fun displayApod(dateString: String) {
        if (dateString.isNotEmpty()) {
            val lastChecked = DateUtils.getRelativeTimeSpanString(PreferenceHelper(activity!!).getLastCheckedDate())
            val apodData = PreferenceHelper(activity!!).getApodData(FileSystemHelper(activity!!), dateString)
            backgroundImage.setImageBitmap(apodData.image)
            titleBar.text = apodData.title
            titleBar.visibility = View.VISIBLE
            descriptionBar.text = apodData.desc
            descriptionBar.visibility = View.VISIBLE
            metadataBar.text = String.format(getString(R.string.last_checked), dateString, lastChecked)
            metadataBar.visibility = View.VISIBLE
            setUpFullscreenButton(apodData.title, dateString)
        }
    }
}