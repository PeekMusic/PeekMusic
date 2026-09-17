package com.metrolist.music.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import com.metrolist.music.playback.MusicService

class PeekWidgetReceiver : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        if (MusicService.isRunning) {
            val intent = Intent(context, MusicService::class.java).apply {
                action = ACTION_UPDATE_WIDGET
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {}
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        if (MusicService.isRunning) {
            val intent = Intent(context, MusicService::class.java).apply {
                action = ACTION_UPDATE_WIDGET
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {}
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        when (intent.action) {
            ACTION_PLAY_PAUSE, ACTION_NEXT, ACTION_PREVIOUS, ACTION_REFETCH_LYRICS, ACTION_TOGGLE_TRANSLATION -> {
                val serviceIntent = Intent(context, MusicService::class.java).apply {
                    action = intent.action
                    putExtras(intent)
                }
                try {
                    context.startService(serviceIntent)
                } catch (e: Exception) {}
            }
        }
    }

    companion object {
        const val ACTION_PLAY_PAUSE = "com.metrolist.music.widget.peek.PLAY_PAUSE"
        const val ACTION_NEXT = "com.metrolist.music.widget.peek.NEXT"
        const val ACTION_PREVIOUS = "com.metrolist.music.widget.peek.PREVIOUS"
        const val ACTION_UPDATE_WIDGET = "com.metrolist.music.widget.peek.UPDATE_WIDGET"
        const val ACTION_REFETCH_LYRICS = "com.metrolist.music.widget.peek.REFETCH_LYRICS"
        const val ACTION_TOGGLE_TRANSLATION = "com.metrolist.music.widget.peek.TOGGLE_TRANSLATION"
    }
}
