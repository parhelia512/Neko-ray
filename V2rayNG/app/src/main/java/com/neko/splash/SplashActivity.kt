package com.neko.splash

import android.content.Intent
import android.content.SharedPreferences
import android.database.Cursor
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import com.neko.v2ray.R
import com.neko.v2ray.ui.BaseActivity
import com.neko.v2ray.ui.MainActivity
import com.neko.uwu.MyDatabaseHelper
import com.neko.uwu.TambahActivity

class SplashActivity : BaseActivity() {

    private lateinit var myDB: MyDatabaseHelper
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        myDB = MyDatabaseHelper(this)

        if (prefs.getBoolean("is_first_launch", true)) {
            setContentView(R.layout.uwu_activity_splash)
            setupWindowStyle()

            Handler(Looper.getMainLooper()).postDelayed({
                launchApp()
                prefs.edit().putBoolean("is_first_launch", false).apply()
            }, 2000)
        } else {
            launchApp()
        }
    }

    private fun setupWindowStyle() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            window.statusBarColor = Color.TRANSPARENT
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        } else {
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            window.statusBarColor = Color.TRANSPARENT
        }
    }

    private fun launchApp() {
        val cursor: Cursor? = myDB.bacaSemuaData()
        if (cursor == null || cursor.count == 0) {
            startActivity(Intent(this, TambahActivity::class.java))
        } else {
            try {
                val db = myDB.readableDatabase
                val query = "SELECT * FROM nekoray"
                val rs: Cursor = db.rawQuery(query, null)

                if (rs.moveToFirst()) {
                    val arrID = rs.getString(rs.getColumnIndexOrThrow("id"))
                    val name = rs.getString(rs.getColumnIndexOrThrow("name"))
                    val posisi = 1

                    val intent = Intent(this, MainActivity::class.java).apply {
                        putExtra("varID", arrID)
                        putExtra("varName", name)
                        putExtra("varPosisi", posisi)
                    }
                    startActivity(intent)
                }
                rs.close()
            } finally {
                cursor?.close()
            }
        }
        finish()
    }
}
