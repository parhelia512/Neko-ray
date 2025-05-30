package com.neko.v2ray.ui

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.RecyclerView
import com.neko.v2ray.handler.SettingsManager
import com.neko.v2ray.helper.CustomDividerItemDecoration
import com.neko.v2ray.util.MyContextWrapper
import com.neko.v2ray.util.Utils
import com.neko.themeengine.Theme
import com.neko.themeengine.ThemeEngine

abstract class BaseActivity : AppCompatActivity() {

    private var lastKnownTheme: Theme? = null
    private var lastKnownNightMode: Int = -1
    private var lastKnownDynamic: Boolean = false
    private var lastKnownTrueBlack: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeEngine.applyToActivity(this)
        ThemeEngine.applyToActivity(this)

        val engine = ThemeEngine.getInstance(this)
        lastKnownTheme = engine.staticTheme
        lastKnownNightMode = engine.themeMode
        lastKnownDynamic = engine.isDynamicTheme
        lastKnownTrueBlack = engine.isTrueBlack
    }

    override fun onResume() {
        super.onResume()
        val engine = ThemeEngine.getInstance(this)

        val themeChanged = engine.staticTheme != lastKnownTheme
        val modeChanged = engine.themeMode != lastKnownNightMode
        val dynamicChanged = engine.isDynamicTheme != lastKnownDynamic
        val trueBlackChanged = engine.isTrueBlack != lastKnownTrueBlack

        if (themeChanged || modeChanged || dynamicChanged || trueBlackChanged) {
            recreate()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        android.R.id.home -> {
            // Handles the home button press by delegating to the onBackPressedDispatcher.
            // This ensures consistent back navigation behavior.
            onBackPressedDispatcher.onBackPressed()
            true
        }

        else -> super.onOptionsItemSelected(item)
    }

    @RequiresApi(Build.VERSION_CODES.N)
    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(MyContextWrapper.wrap(newBase ?: return, SettingsManager.getLocale()))
    }

    /**
     * Adds a custom divider to a RecyclerView.
     *
     * @param recyclerView  The target RecyclerView to which the divider will be added.
     * @param context       The context used to access resources.
     * @param drawableResId The resource ID of the drawable to be used as the divider.
     * @param orientation   The orientation of the divider (DividerItemDecoration.VERTICAL or DividerItemDecoration.HORIZONTAL).
     */
    fun addCustomDividerToRecyclerView(recyclerView: RecyclerView, context: Context?, drawableResId: Int, orientation: Int = DividerItemDecoration.VERTICAL) {
        // Get the drawable from resources
        val drawable = ContextCompat.getDrawable(context!!, drawableResId)
        requireNotNull(drawable) { "Drawable resource not found" }

        // Create a DividerItemDecoration with the specified orientation
        val dividerItemDecoration = CustomDividerItemDecoration(drawable, orientation)

        // Add the divider to the RecyclerView
        recyclerView.addItemDecoration(dividerItemDecoration)
    }
}
