package com.realsub.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private val REQ_PROJECTION = 1001
    private val REQ_NOTIF = 1002

    private lateinit var prefs: Prefs
    private lateinit var tvPerm: TextView
    private lateinit var tvRegion: TextView
    private lateinit var tvSize: TextView
    private lateinit var tvBottom: TextView

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)

        tvPerm = findViewById(R.id.tvPerm)
        tvRegion = findViewById(R.id.tvRegion)
        tvSize = findViewById(R.id.tvSize)
        tvBottom = findViewById(R.id.tvBottom)

        val etKey: EditText = findViewById(R.id.etKey)
        val etModel: EditText = findViewById(R.id.etModel)
        val rg: RadioGroup = findViewById(R.id.rgHard)
        val sbSize: SeekBar = findViewById(R.id.sbSize)
        val sbBottom: SeekBar = findViewById(R.id.sbBottom)
        val cbEn: CheckBox = findViewById(R.id.cbEn)

        etKey.setText(prefs.apiKey)
        etModel.setText(prefs.model)
        etKey.addTextChangedListener(saver { prefs.apiKey = it })
        etModel.addTextChangedListener(saver { prefs.model = it })

        rg.check(when (prefs.hard) {
            0 -> R.id.rb0
            1 -> R.id.rb1
            3 -> R.id.rb3
            else -> R.id.rb2
        })
        rg.setOnCheckedChangeListener { _, id ->
            prefs.hard = when (id) {
                R.id.rb0 -> 0
                R.id.rb1 -> 1
                R.id.rb3 -> 3
                else -> 2
            }
        }

        sbSize.progress = prefs.textSize - 12
        sbBottom.progress = prefs.bottomPct
        cbEn.isChecked = prefs.showEn
        labels()

        sbSize.setOnSeekBarChangeListener(seek { prefs.textSize = it + 12; labels() })
        sbBottom.setOnSeekBarChangeListener(seek { prefs.bottomPct = it; labels() })
        cbEn.setOnCheckedChangeListener { _, v -> prefs.showEn = v }

        findViewById<Button>(R.id.btnOverlayPerm).setOnClickListener { askOverlay() }
        findViewById<Button>(R.id.btnRegion).setOnClickListener {
            if (!canOverlay()) { askOverlay(); return@setOnClickListener }
            startActivity(Intent(this, RegionActivity::class.java))
        }
        findViewById<Button>(R.id.btnStart).setOnClickListener { start() }
        findViewById<Button>(R.id.btnStop).setOnClickListener { stop() }

        askNotif()
    }

    override fun onResume() {
        super.onResume()
        status()
    }

    private fun labels() {
        tvSize.text = "Yazı boyutu: ${prefs.textSize}"
        tvBottom.text = "Alttan uzaklık: %${prefs.bottomPct}"
    }

    private fun status() {
        tvPerm.text = if (canOverlay())
            "Ekranın üstüne çizme izni verilmiş."
        else
            "Ekranın üstüne çizme izni gerekiyor — altyazı bu izinle görünür."

        tvRegion.text = if (prefs.regionSet)
            "Seçili alan: soldan %${(prefs.regionL * 100).toInt()}, üstten %${(prefs.regionT * 100).toInt()}, " +
                    "genişlik %${((prefs.regionR - prefs.regionL) * 100).toInt()}"
        else
            "Alan seçilmedi — varsayılan olarak ekranın alt şeridi okunur."
    }

    private fun canOverlay(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)

    private fun askOverlay() {
        if (canOverlay()) { toast("İzin zaten verilmiş."); return }
        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
    }

    private fun askNotif() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIF)
        }
    }

    private fun start() {
        if (!canOverlay()) { askOverlay(); return }
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        @Suppress("DEPRECATION")
        startActivityForResult(mpm.createScreenCaptureIntent(), REQ_PROJECTION)
    }

    private fun stop() {
        val i = Intent(this, CaptureService::class.java).setAction(CaptureService.ACTION_STOP)
        startService(i)
        toast("Durduruldu.")
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(req, res, data)
        if (req != REQ_PROJECTION) return
        if (res != Activity.RESULT_OK || data == null) { toast("Ekran izni verilmedi."); return }

        val i = Intent(this, CaptureService::class.java)
            .setAction(CaptureService.ACTION_START)
            .putExtra(CaptureService.EXTRA_CODE, res)
            .putExtra(CaptureService.EXTRA_DATA, data)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i) else startService(i)

        toast("Başladı — uygulamadan çıkıp izlemeye başla.")
        moveTaskToBack(true)
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun saver(f: (String) -> Unit) = object : TextWatcher {
        override fun afterTextChanged(s: Editable?) { f(s?.toString() ?: "") }
        override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
    }

    private fun seek(f: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(sb: SeekBar?, v: Int, fromUser: Boolean) { f(v) }
        override fun onStartTrackingTouch(sb: SeekBar?) {}
        override fun onStopTrackingTouch(sb: SeekBar?) {}
    }
}
