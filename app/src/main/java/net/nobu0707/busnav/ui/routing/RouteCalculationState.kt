package net.nobu0707.busnav.ui.routing

import net.nobu0707.busnav.domain.route.ScheduledRoute
import net.nobu0707.busnav.domain.routing.RoutingFailure
import net.nobu0707.busnav.domain.routing.RoutingSummary

sealed interface RouteCalculationState {
    data object Idle : RouteCalculationState
    data class Calculating(val planRevision: Long) : RouteCalculationState
    data class Success(
        val planRevision: Long,
        val route: ScheduledRoute,
        val summary: RoutingSummary,
    ) : RouteCalculationState
    data class Failure(val planRevision: Long, val reason: RoutingFailure) : RouteCalculationState
}

fun RoutingFailure.userMessage(): String = when (this) {
    RoutingFailure.NO_ROUTE -> "大型車条件で走行可能な経路が見つかりませんでした"
    RoutingFailure.NETWORK, RoutingFailure.SERVICE_UNAVAILABLE -> "経路探索サーバーに接続できません"
    RoutingFailure.TIMEOUT -> "経路探索がタイムアウトしました"
    RoutingFailure.RATE_LIMITED -> "経路探索サービスが混雑しています"
    RoutingFailure.CONFIGURATION -> "経路探索サーバーが設定されていません"
    RoutingFailure.INVALID_RESPONSE -> "経路探索結果を読み取れませんでした"
    RoutingFailure.SERVER_ERROR -> "経路探索サーバーでエラーが発生しました"
    RoutingFailure.INVALID_REQUEST -> "経路探索の入力内容を確認してください"
}
