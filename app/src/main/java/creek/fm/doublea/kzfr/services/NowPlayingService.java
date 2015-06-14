package creek.fm.doublea.kzfr.services;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.IBinder;
import android.os.PowerManager;

import java.io.IOException;

/**
 * Created by Aaron on 6/10/2015.
 */
public class NowPlayingService extends Service implements AudioManager.OnAudioFocusChangeListener,
        MediaPlayer.OnPreparedListener, MediaPlayer.OnErrorListener, MediaPlayer.OnBufferingUpdateListener, MediaPlayer.OnInfoListener {

    private final IBinder mMediaPlayerBinder = new MediaPlayerBinder();
    public static final String ACTION_PLAY = "creek.fm.doublea.kzfr.services.PLAY";
    public static final String ACTION_PAUSE = "creek.fm.doublea.kzfr.services.PAUSE";
    public static final String ACTION_CLOSE = "creek.fm.doublea.kzfr.services.APP_CLOSING";
    private MediaPlayer mMediaPlayer = null;
    private MediaSession mMediaSession = null;
    private MediaController mMediaController = null;
    private AudioManager mAudioManager = null;

    //The URL that feeds the KZFR stream.
    private static final String mStreamUrl = "http://stream-tx1.radioparadise.com:8090/;stream/1";

    //Wifi Lock to ensure the wifo does not ge to sleep while we are stearming music.
    WifiManager.WifiLock mWifiLock;

    enum State {
        Retrieving, // the MediaRetriever is retrieving music
        Stopped,  //Media player is stopped and not prepared to play
        Preparing, // Media player is preparing to play
        Playeng,  // MediaPlayer playback is active.
        // There is a chance that the MP is actually paused here if we do not have audio focus.
        // We stay in this state so we know to resume when we gain audio focus again.
        Paused // Audio Playback is paused
    }

    State mState = State.Stopped;

    enum AudioFocus {
        NoFocusNoDuck, // service does not have audio focus and cannot duck
        NoFocusCanDuck, // we don't have focus but we can play at low volume ("ducking")
        Focused  // media player has full audio focus
    }

    AudioFocus mAudioFocus = AudioFocus.NoFocusNoDuck;

    @Override
    public void onAudioFocusChange(int focusChange) {
        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_GAIN:
                mAudioFocus = AudioFocus.Focused;
                // resume playback
                if (mState == State.Playeng) {
                    startMediaPlayer();
                    mMediaPlayer.setVolume(1.0f, 1.0f);
                }
                break;

            case AudioManager.AUDIOFOCUS_LOSS:
                mAudioFocus = AudioFocus.NoFocusNoDuck;
                // Lost focus for an unbounded amount of time: stop playback and release media player
                stopMediaPlayer();
                break;

            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                mAudioFocus = AudioFocus.NoFocusNoDuck;
                // Lost focus for a short time, but we have to stop
                // playback. We don't release the media player because playback
                // is likely to resume
                pauseMediaPlayer();
                break;

            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                mAudioFocus = AudioFocus.NoFocusCanDuck;
                // Lost focus for a short time, but it's ok to keep playing
                // at an attenuated level
                if (mMediaPlayer.isPlaying()) mMediaPlayer.setVolume(0.1f, 0.1f);
                break;
        }
    }


    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        return false;
    }

    @Override
    public void onBufferingUpdate(MediaPlayer mp, int percent) {

    }

    @Override
    public boolean onInfo(MediaPlayer mp, int what, int extra) {
        return false;
    }


    @Override
    public void onCreate() {
        super.onCreate();

    }

    private void setupAudioManager() {
        if (mAudioManager == null) {
            mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        }
    }

    private void setupWifiLock() {
        if (mWifiLock == null) {
            mWifiLock = ((WifiManager) getSystemService(Context.WIFI_SERVICE))
                    .createWifiLock(WifiManager.WIFI_MODE_FULL, "mediaplayerlock");
        }
    }

    private void setupMediaPlayer() {
        if (mMediaPlayer == null) {
            mMediaPlayer = new MediaPlayer();
            mMediaPlayer.setOnPreparedListener(this);
            mMediaPlayer.setOnErrorListener(this);
            mMediaPlayer.setOnBufferingUpdateListener(this);
            mMediaPlayer.setOnInfoListener(this);
            mMediaPlayer.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);
            mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            try {
                mMediaPlayer.setDataSource(this, Uri.parse(mStreamUrl));
            } catch (IOException e) {
                e.printStackTrace();
                stopSelf();
            }
        }
    }

    /*
        The radio streaming service runs in forground mode to keep the Android OS from killing it.
        The OnStartCommand is called every time there is a call to start service and the service is
        already started. By Passing an intent to the onStartCommand we can play and pause the music.
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = null;
        if (intent != null) {
            action = intent.getAction();
        }
        if (action != null) {
            if (action.equals(ACTION_PLAY)) {
                processPlayRequest();
            } else if (action.equals(ACTION_PAUSE)) {
                processPauseRequest();
            }
        }
        return START_STICKY; //do not restart service if it is killed.
    }

    private void initMediaPlayer() {
        setupMediaPlayer();
        requestResources();
    }

    /*
        Check if the media player was initialized and we have audio focus.
        Without audio focus we do not start the media player.
        change state and start to prepare async
     */
    private void configAndPrepareMediaPlayer() {
        initMediaPlayer();
        mState = State.Preparing;
        mMediaPlayer.prepareAsync();

    }

    /*
        The media player is prepared check to make sure we are not in the stopped or paused states
        before starting the media player
     */
    @Override
    public void onPrepared(MediaPlayer mp) {
        if (mState != State.Paused && mState != State.Stopped) {
            startMediaPlayer();
        }
    }

    /*
        Check if the media player is available and start it.
     */
    private void startMediaPlayer() {
        if (mMediaPlayer != null) {
            mMediaPlayer.start();
            mState = State.Playeng;
        }
    }

    /*
        Request audio focus and aquire a wifi lock. Returns true if audio focus was granted.
     */
    private void requestResources() {
        setupAudioManager();
        setupWifiLock();
        mWifiLock.acquire();

        tryToGetAudioFocus();

        //this was explicit logic of getting audio focus. Instead just make the attempt and move on.
        // if we do not have audio focus it will not play but the state will be changed and the media
        //player  will have been set up. The music will play when we gain audio focus.
        /*//if audio focus is something other than focused then ask for focus from the audio manager.
        //if audio focus is already focused then simply return true.
        if (mAudioFocus != AudioFocus.Focused) {
            if (AudioManager.AUDIOFOCUS_REQUEST_GRANTED ==
                    mAudioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC,
                            AudioManager.AUDIOFOCUS_GAIN)) {
                mAudioFocus = AudioFocus.Focused;
                return true;
            } else
                return false;
        }
        return true;*/
    }

    private void tryToGetAudioFocus() {
        if (mAudioFocus != AudioFocus.Focused && AudioManager.AUDIOFOCUS_REQUEST_GRANTED ==
                mAudioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC,
                        AudioManager.AUDIOFOCUS_GAIN))
            mAudioFocus = AudioFocus.Focused;

    }

    /*
        if the Media player is playing then stop it. Change the state and relax the wifi lock and
        audio focus.
     */
    private void stopMediaPlayer() {
        // Lost focus for an unbounded amount of time: stop playback and release media player
        if (mMediaPlayer != null) {
            if (mMediaPlayer.isPlaying()) mMediaPlayer.stop();
            mMediaPlayer.release();
            mMediaPlayer = null;
        }
        mState = State.Stopped;

        //relax the resources because we no longer need them.
        relaxResources();
        giveUpAudioFocus();
    }


    private void pauseMediaPlayer() {
        if (mMediaPlayer != null && mMediaPlayer.isPlaying()) {
            mMediaPlayer.pause();
            mState = State.Paused;
        }
    }

    private void processPlayRequest() {
        if (mState == State.Stopped) {
            configAndPrepareMediaPlayer();
        } else if (mState == State.Paused) {
            requestResources();
            startMediaPlayer();
        }

    }

    private void processPauseRequest() {

        if (mState != State.Stopped) {  // process the pause request unless the media player is in the stopped state.
            if (mState == State.Playeng) { //if we are in the playing state then the media player needs to be paused
                mMediaPlayer.pause();
            }
            mState = State.Paused;
            relaxResources();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mMediaPlayerBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        return false;
    }

    @Override
    public void onDestroy() {
        stopMediaPlayer();
    }

    //give up wifi lock if it is held and stop the service from being a foreground service.
    private void relaxResources() {

        //Release the WifiLock resource
        if (mWifiLock.isHeld()) {
            mWifiLock.release();
        }

        // stop service from being a foreground service. Passing true removes the notification as well.
        stopForeground(true);

    }

    private void giveUpAudioFocus() {
        if ((mAudioFocus == AudioFocus.Focused || mAudioFocus == AudioFocus.NoFocusCanDuck) &&
                AudioManager.AUDIOFOCUS_REQUEST_GRANTED == mAudioManager.abandonAudioFocus(this)) {
            mAudioFocus = AudioFocus.NoFocusNoDuck;
        }
    }

    public class MediaPlayerBinder extends Binder {

        public NowPlayingService getService() {
            return NowPlayingService.this;
        }

        public boolean isPlaying() {
            if (mMediaPlayer != null) {
                return mMediaPlayer.isPlaying();
            } else {
                return false;
            }
        }
    }
}
