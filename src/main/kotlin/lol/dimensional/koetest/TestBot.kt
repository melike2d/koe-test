package lol.dimensional.koetest

import com.sedmelluq.discord.lavaplayer.format.StandardAudioDataFormats
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager
import com.sedmelluq.discord.lavaplayer.player.FunctionalResultHandler
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.playback.MutableAudioFrame
import dev.kord.core.Kord
import dev.kord.core.event.gateway.ReadyEvent
import dev.kord.core.event.message.MessageCreateEvent
import dev.kord.core.on
import dev.kord.gateway.*
import io.netty.buffer.ByteBuf
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import moe.kyokobot.koe.Koe
import moe.kyokobot.koe.KoeClient
import moe.kyokobot.koe.MediaConnection
import moe.kyokobot.koe.VoiceServerInfo
import moe.kyokobot.koe.media.OpusAudioFrameProvider
import mu.KotlinLogging
import java.nio.ByteBuffer
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

lateinit var koeClient: KoeClient
lateinit var controller: Controller
lateinit var kord: Kord
val logger = KotlinLogging.logger { }

suspend fun main(args: Array<String>) {
    val koe = Koe.koe()
    val players = DefaultAudioPlayerManager()

    kord = Kord(args.firstOrNull() ?: throw error("i need a token bozo"))

    AudioSourceManagers.registerRemoteSources(players)

    kord.on<ReadyEvent> {
        koeClient = koe.newClient(self.id.value)
        logger.info { "Ready!!" }
    }

    kord.on<MessageCreateEvent> {
        if (!message.content.startsWith("!")) {
            return@on
        }

        val args = message.content.drop(1).trim().split("\\s+".toRegex()).toMutableList()
        when (args.removeFirst()) {
            "ping" -> message.channel.createMessage("pong!")

            "join" -> {
                val vc = message.getAuthorAsMember()!!.getVoiceStateOrNull()!!.getChannelOrNull()
                    ?: return@on

                controller = Controller(players.createPlayer(), vc.guildId.value)
                vc.guild.gateway!!.send(
                    UpdateVoiceStatus(
                        guildId = vc.guildId,
                        channelId = vc.id,
                        selfMute = false,
                        selfDeaf = false
                    )
                )
            }

            "play" -> {
                if (!::controller.isInitialized) {
                    message.channel.createMessage("use !join bozo")
                    return@on
                }

                val track = suspendCoroutine<AudioTrack> { cont ->
                    players.loadItem("ytsearch:${args.joinToString(" ")}", FunctionalResultHandler(
                        { cont.resume(it) },
                        { cont.resume(it.tracks.first()) },
                        { },
                        { }
                    ))
                }

                controller.provide()
                controller.player.playTrack(track)
            }
        }
    }

    kord.login()
}

class Controller(val player: AudioPlayer, guildId: Long) : CoroutineScope {
    val connection = koeClient.createConnection(guildId)

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Default + SupervisorJob()

    init {
        launch {
            val serverInfo = withTimeoutOrNull(2000) {
                val gateway = kord.gateway.gateways.values.first()
                val voiceStateUpdate = gateway.events.filterIsInstance<VoiceStateUpdate>().first().voiceState
                val voiceServerUpdate = gateway.events.filterIsInstance<VoiceServerUpdate>().first().voiceServerUpdateData

                VoiceServerInfo(voiceStateUpdate.sessionId, voiceServerUpdate.endpoint!!, voiceServerUpdate.token)
            } ?: throw error("didn't receive voice server info in time.")

            connection.connect(serverInfo)
        }
    }

    fun provide() {
        FrameProvider(player, connection)
    }
}

class FrameProvider(val player: AudioPlayer, connection: MediaConnection) : OpusAudioFrameProvider(connection) {
    private val frame = MutableAudioFrame()
    private val buffer = ByteBuffer.allocateDirect(StandardAudioDataFormats.DISCORD_OPUS.maximumChunkSize())

    init {
        frame.setBuffer(buffer)
        connection.audioSender = this
    }

    override fun canProvide(): Boolean {
        return player.provide(frame)
    }

    override fun retrieveOpusFrame(targetBuffer: ByteBuf) {
        targetBuffer.writeBytes(buffer.flip())
    }
}
