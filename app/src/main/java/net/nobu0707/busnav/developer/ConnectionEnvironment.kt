package net.nobu0707.busnav.developer

import net.nobu0707.busnav.map.basemap.BasemapRegion

enum class ConnectionEnvironment(val id: String, val label: String) {
    LOCAL_EMULATOR("local_emulator", "Local Emulator"),
    LOCAL_LAN("local_lan", "Local LAN"),
    REMOTE_TEST("remote_test", "Remote Test / 全国"),
    CUSTOM("custom", "Custom"),
    ;

    companion object {
        // Missing/unknown keys must never opt existing installs into remote services.
        fun fromId(id: String?) = entries.firstOrNull { it.id == id } ?: CUSTOM
    }
}

object RemoteTestEndpoints {
    const val ROUTING = "https://routing-busnav.nobu0707.net"
    const val MAP = "https://maps-busnav.nobu0707.net"
    const val LIGHT_STYLE = "$MAP/styles/busnav-light/style.json"
    const val DARK_STYLE = "$MAP/styles/busnav/style.json"
    const val IPV6_HINT = "現在のリモートテストサーバーはIPv6接続が必要です"

    fun settings() = DeveloperConnectionSettings(ROUTING, MAP, BasemapRegion.JAPAN,
        ConnectionEnvironment.REMOTE_TEST)
}

fun DeveloperConnectionSettings.forEnvironment(environment: ConnectionEnvironment): DeveloperConnectionSettings =
    when (environment) {
        ConnectionEnvironment.REMOTE_TEST -> RemoteTestEndpoints.settings()
        ConnectionEnvironment.LOCAL_EMULATOR -> DeveloperConnectionSettings(
            "http://10.0.2.2:8002", "http://10.0.2.2:8080",
            BasemapRegion.JAPAN, environment)
        ConnectionEnvironment.LOCAL_LAN, ConnectionEnvironment.CUSTOM ->
            copy(selectedConnectionEnvironment = environment, basemapRegion = BasemapRegion.JAPAN)
    }

object ConnectionSwitchPolicy {
    fun canEdit(navigationActive: Boolean) = !navigationActive
    fun requireEditable(navigationActive: Boolean) {
        check(canEdit(navigationActive)) { "ナビゲーションを終了してから接続設定を変更してください" }
    }
}

fun ConnectionResult.failureHint(environment: ConnectionEnvironment): String? =
    RemoteTestEndpoints.IPV6_HINT.takeIf {
        environment == ConnectionEnvironment.REMOTE_TEST && status != ConnectionStatus.SUCCESS &&
            status != ConnectionStatus.INVALID_URL
    }
