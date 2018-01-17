package weian.cheng.mediaplayerwithexoplayer

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.android.exoplayer2.ExoPlaybackException
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.ExoPlayerFactory
import com.google.android.exoplayer2.PlaybackParameters
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.Player.STATE_BUFFERING
import com.google.android.exoplayer2.Player.STATE_ENDED
import com.google.android.exoplayer2.Player.STATE_IDLE
import com.google.android.exoplayer2.Player.STATE_READY
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.Timeline
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory
import com.google.android.exoplayer2.source.ExtractorMediaSource
import com.google.android.exoplayer2.source.TrackGroupArray
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.trackselection.TrackSelectionArray
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.util.Util
import weian.cheng.mediaplayerwithexoplayer.ExoPlayerEventListener.PlayerEventListener
import java.lang.Exception

import weian.cheng.mediaplayerwithexoplayer.MusicPlayerState.Standby
import weian.cheng.mediaplayerwithexoplayer.MusicPlayerState.Pause
import weian.cheng.mediaplayerwithexoplayer.MusicPlayerState.Play

/**
 * Created by weian on 2017/11/28.
 *
 */

class ExoPlayerWrapper(context: Context): IMusicPlayer {

    private val TAG = "ExoPlayerWrapper"
    private var context: Context
    private lateinit var exoPlayer: SimpleExoPlayer
    private var isPlaying = false
    private lateinit var timer: PausableTimer
    private var playerState = Standby

    private var listener: PlayerEventListener ?= null

    init {
        this.context = context
    }

    override fun play(uri: String) {
        if (playerState == Play) {
            // TODO: find out a appropriate exception or make one.
            throw Exception("now is playing")
        }

        initExoPlayer(uri)
        exoPlayer.playWhenReady = true
        setPlayerState(Play)
    }

    override fun play() {
        when (isPlaying) {
            true -> {
                timer.pause()
                setPlayerState(Pause)
            }

            false -> {
                timer.resume()
                setPlayerState(Play)
            }
        }

        if (isPlaying) {
            timer.pause()
            setPlayerState(Pause)
        } else {
            timer.resume()
            setPlayerState(Play)
        }
        exoPlayer.playWhenReady = !isPlaying
    }

    override fun stop() {
        exoPlayer.playWhenReady = false
        exoPlayer.stop()
        timer.stop()
        setPlayerState(Standby)
    }

    override fun pause() {
        exoPlayer.playWhenReady = false
        timer.pause()
        setPlayerState(Pause)
    }

    override fun resume() {
        exoPlayer.playWhenReady = true
        timer.resume()
        setPlayerState(Play)
    }

    override fun setRepeat(isRepeat: Boolean) {
        if (isRepeat)
            exoPlayer.repeatMode = Player.REPEAT_MODE_ONE
        else
            exoPlayer.repeatMode = Player.REPEAT_MODE_OFF
    }

    override fun seekTo(sec: Int) {
        exoPlayer.seekTo(sec.times(1000).toLong())
    }

    override fun getPlayerState() = playerState

    override fun writeToFile(uri: String): Boolean {
        return true
    }

    override fun setEventListener(listener: PlayerEventListener) {
        this.listener = listener
    }

    private fun setPlayerState(state: MusicPlayerState) {
        if (state != playerState) {
            playerState = state
            listener?.onPlayerStateChanged(state)
        }
    }

    private fun initExoPlayer(url: String) {
        Log.i(TAG, "initExoPlayer")
        val meter = DefaultBandwidthMeter()
        val dataSourceFactory = DefaultDataSourceFactory(context, Util.getUserAgent(context, "LocalExoPlayer"), meter)
        val uri = Uri.parse(url)
        val extractorMediaSource = ExtractorMediaSource(uri, dataSourceFactory, DefaultExtractorsFactory(), null, null)
        val trackSelector = DefaultTrackSelector(meter)

        exoPlayer = ExoPlayerFactory.newSimpleInstance(context, trackSelector)
        exoPlayer.addListener(LocalPlayerEventListener(this, exoPlayer))
        exoPlayer.prepare(extractorMediaSource)
    }

    private class LocalPlayerEventListener(player: ExoPlayerWrapper,
                                           exoplayer: ExoPlayer): Player.EventListener {

        private var exoPlayer: ExoPlayer
        private var musicPlayer: ExoPlayerWrapper

        init {
            exoPlayer = exoplayer
            musicPlayer = player
        }

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters?) {
        }

        override fun onTracksChanged(trackGroups: TrackGroupArray?, trackSelections: TrackSelectionArray?) {
        }

        override fun onPlayerError(error: ExoPlaybackException?) {
        }

        override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
            musicPlayer.isPlaying = playWhenReady
            if (playbackState == STATE_ENDED) {
                musicPlayer.setPlayerState(Standby)
            }
        }

        override fun onLoadingChanged(isLoading: Boolean) {
            musicPlayer.listener?.onBufferPercentage(exoPlayer.bufferedPercentage)
        }

        override fun onPositionDiscontinuity() {
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
        }

        override fun onTimelineChanged(timeline: Timeline?, manifest: Any?) {
            if (exoPlayer.duration > 0) {
                musicPlayer.listener?.onDurationChanged(exoPlayer.duration.div(1000).toInt())
            }

            musicPlayer.timer = PausableTimer(exoPlayer.duration, 1000)
            musicPlayer.timer.onTick = { millisUntilFinished ->
                musicPlayer.listener?.onCurrentTime(millisUntilFinished.div(1000).toInt())
            }
            musicPlayer.timer.onFinish = {
                musicPlayer.listener?.onCurrentTime(exoPlayer.duration.div(1000).toInt())
            }
            musicPlayer.timer.start()
        }
    }
}