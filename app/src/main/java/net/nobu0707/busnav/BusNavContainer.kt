package net.nobu0707.busnav

import android.content.Context
import androidx.room.Room
import net.nobu0707.busnav.data.storage.prescribed.PrescribedRouteDatabase
import net.nobu0707.busnav.data.storage.prescribed.RoomPrescribedRouteRepository

class BusNavContainer private constructor(context: Context) {
    private val database = Room.databaseBuilder(context.applicationContext,
        PrescribedRouteDatabase::class.java, "prescribed-routes.db").build()
    val prescribedRoutes = RoomPrescribedRouteRepository(database)
    companion object {
        @Volatile private var instance: BusNavContainer? = null
        fun get(context: Context): BusNavContainer = instance ?: synchronized(this) {
            instance ?: BusNavContainer(context).also { instance = it }
        }
    }
}