package com.neko.v2ray.ui

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import com.neko.v2ray.R

class NekoAboutFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.uwu_preferences_about, rootKey)
    }
}
