package com.helsinkiwizard.cointoss.datalayer

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.wear.tiles.TileService
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.helsinkiwizard.cointoss.Repository
import com.helsinkiwizard.cointoss.WatchApplication
import com.helsinkiwizard.cointoss.tile.CoinTileService
import com.helsinkiwizard.cointoss.ui.MainActivity
import com.helsinkiwizard.core.CoreConstants.BYTE_BUFFER_CAPACITY
import com.helsinkiwizard.core.CoreConstants.IMAGE_PATH
import com.helsinkiwizard.core.CoreConstants.START_ACTIVITY_PATH
import com.helsinkiwizard.core.coin.CoinType
import com.helsinkiwizard.core.utils.deleteBitmap
import com.helsinkiwizard.core.utils.storeBitmap
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.nio.ByteBuffer
import javax.inject.Inject


@AndroidEntryPoint
class DataLayerListenerService : WearableListenerService() {

    @Inject
    lateinit var repo: Repository

    private val channelClient by lazy { Wearable.getChannelClient(this) }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val channelCallback = object : ChannelClient.ChannelCallback() {
        override fun onChannelOpened(channel: ChannelClient.Channel) {
            if (channel.path == IMAGE_PATH) {
                receiveImage(channel)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        channelClient.registerChannelCallback(channelCallback)
    }

    override fun onDestroy() {
        super.onDestroy()
        channelClient.unregisterChannelCallback(channelCallback)
        scope.cancel()
    }

    // Suppressed because MainActivity is set to "singleTop" in manifest
    @SuppressLint("WearRecents")
    override fun onMessageReceived(messageEvent: MessageEvent) {
        super.onMessageReceived(messageEvent)
        when (messageEvent.path) {
            START_ACTIVITY_PATH -> {
                val isAppInForeground = (application as WatchApplication).lifecycleObserver.isAppInForeground
                if (isAppInForeground.not()) {
                    startActivity(
                        Intent(this, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
        }
    }

    private fun receiveImage(channel: ChannelClient.Channel) {
        scope.launch {
            val inputStream = channelClient.getInputStream(channel).await()
            val byteArray = inputStream.readBytes()
            val (heads, tails, name) = byteArray.toBitmapAndString()
            inputStream.close()

            updateImage(heads, tails, name)
        }
    }

    private suspend fun updateImage(heads: Bitmap, tails: Bitmap, name: String) {
        val headsUri = storeBitmap(applicationContext, heads)
        val tailsUri = storeBitmap(applicationContext, tails)

        if (headsUri != null && tailsUri != null) {
            val oldCoin = repo.getCustomCoin.firstOrNull()
            repo.setCustomCoin(headsUri, tailsUri, name)
            repo.setCoinType(CoinType.CUSTOM)
            TileService.getUpdater(applicationContext).requestUpdate(CoinTileService::class.java)

            if (oldCoin != null) {
                deleteBitmap(applicationContext, oldCoin.headsUri)
                deleteBitmap(applicationContext, oldCoin.tailsUri)
            }
        }
    }

    private fun ByteArray.toBitmapAndString(): Triple<Bitmap, Bitmap, String> {
        var offset = 0

        fun readInt(): Int {
            val intBytes = this.copyOfRange(offset, offset + BYTE_BUFFER_CAPACITY)
            offset += BYTE_BUFFER_CAPACITY
            return ByteBuffer.wrap(intBytes).int
        }

        fun readByteArray(size: Int): ByteArray {
            val byteArray = this.copyOfRange(offset, offset + size)
            offset += size
            return byteArray
        }

        val headsSize = readInt()
        val headsData = readByteArray(headsSize)
        val headsBitmap = BitmapFactory.decodeByteArray(headsData, 0, headsSize)

        val tailsSize = readInt()
        val tailsData = readByteArray(tailsSize)
        val tailsBitmap = BitmapFactory.decodeByteArray(tailsData, 0, tailsSize)

        val nameSize = readInt()
        val nameData = readByteArray(nameSize)
        val name = String(nameData)

        return Triple(headsBitmap, tailsBitmap, name)
    }
}
