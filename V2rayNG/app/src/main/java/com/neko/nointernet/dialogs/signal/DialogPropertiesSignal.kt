package com.neko.nointernet.dialogs.signal

import com.neko.nointernet.dialogs.base.BaseDialogProperties

class DialogPropertiesSignal(
    var noInternetConnectionTitle: String = "",
    var noInternetConnectionMessage: String = "",
    var showInternetOnButtons: Boolean = false,
    var pleaseTurnOnText: String = "",
    var wifiOnButtonText: String = "",
    var mobileDataOnButtonText: String = "",
    var onAirplaneModeTitle: String = "",
    var onAirplaneModeMessage: String = "",
    var pleaseTurnOffText: String = "",
    var airplaneModeOffButtonText: String = "",
    var showAirplaneModeOffButtons: Boolean = false
) : BaseDialogProperties()