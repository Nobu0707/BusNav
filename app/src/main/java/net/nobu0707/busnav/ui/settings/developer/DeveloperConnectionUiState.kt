package net.nobu0707.busnav.ui.settings.developer

import net.nobu0707.busnav.developer.ConnectionResult
import net.nobu0707.busnav.developer.ConnectionStatus
import net.nobu0707.busnav.developer.ConnectionEnvironment
import net.nobu0707.busnav.developer.failureHint

fun ConnectionResult.message(environment: ConnectionEnvironment = ConnectionEnvironment.CUSTOM): String = when (status) {
    ConnectionStatus.SUCCESS -> "接続成功"
    ConnectionStatus.HTTP_ERROR -> "HTTPエラー ($httpCode)"
    ConnectionStatus.TIMEOUT -> "タイムアウト"
    ConnectionStatus.HOST_ERROR -> "ホストに接続できません（DNS・アドレス・ネットワークを確認）"
    ConnectionStatus.INVALID_URL -> "URLの形式が正しくありません"
} + (failureHint(environment)?.let { "\n$it" } ?: "")
