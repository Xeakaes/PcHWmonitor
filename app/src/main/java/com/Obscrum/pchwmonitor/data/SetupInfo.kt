package com.Obscrum.pchwmonitor.data

object SetupInfo {
    fun shouldShow(setupInfoShown: Boolean, serverCount: Int): Boolean =
        !setupInfoShown && serverCount == 0
}
