package io.flutter.plugins.videoplayer;

import static com.google.android.exoplayer2.Player.REPEAT_MODE_ALL;
import static com.google.android.exoplayer2.Player.REPEAT_MODE_OFF;

import android.content.Context;
import android.net.Uri;
import java.net.*;
import java.net.URL;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.io.BufferedReader;
import android.os.StrictMode;
import android.util.Log;
import java.util.Scanner;
import android.view.Surface;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Player.EventListener;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.RendererCapabilities;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.source.dash.DefaultDashChunkSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.ui.TrackNameProvider;
import com.google.android.exoplayer2.ui.DefaultTrackNameProvider;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.smoothstreaming.DefaultSsChunkSource;
import com.google.android.exoplayer2.source.smoothstreaming.SsMediaSource;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;
import com.google.android.exoplayer2.util.Util;
import io.flutter.plugin.common.EventChannel;
import io.flutter.view.TextureRegistry;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class VideoPlayer {
  private static final String FORMAT_SS = "ss";
  private static final String FORMAT_DASH = "dash";
  private static final String FORMAT_HLS = "hls";
  private static final String FORMAT_OTHER = "other";

  private SimpleExoPlayer exoPlayer;

  private Surface surface;

  private final TextureRegistry.SurfaceTextureEntry textureEntry;

  private QueuingEventSink eventSink = new QueuingEventSink();

  DefaultTrackSelector trackSelector ;

  private final EventChannel eventChannel;

  private boolean isInitialized = false;

  private final VideoPlayerOptions options;

  Context context;

  VideoPlayer(
      Context context,
      EventChannel eventChannel,
      TextureRegistry.SurfaceTextureEntry textureEntry,
      String dataSource,
      String formatHint,
      VideoPlayerOptions options) {
    this.eventChannel = eventChannel;
    this.textureEntry = textureEntry;
    this.options = options;
    this.context = context;

    StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
    StrictMode.setThreadPolicy(policy);

    trackSelector = new DefaultTrackSelector(context);
    exoPlayer = new SimpleExoPlayer.Builder(context).setTrackSelector(trackSelector).build();

    Uri uri = Uri.parse(dataSource);

    DataSource.Factory dataSourceFactory;

    String userAg = "123";
    try {        
      URL url = new URL("http://gambatvonline.com/backend/public/api/movie/get_useragent");
      // read text returned by server
      BufferedReader in = new BufferedReader(new InputStreamReader(url.openStream()));
      userAg = in.readLine();
      in.close();
      userAg = userAg.replace("\"", "");  
    }
    catch (MalformedURLException e) {
      Log.d(" MalformedURLException aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", e.getMessage());
    }
    catch (IOException e) {
      Log.d(" IOException aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", e.getMessage());
    }

    if (isHTTP(uri)) {
      dataSourceFactory =
          new DefaultHttpDataSourceFactory(
              userAg,
              null,
              DefaultHttpDataSource.DEFAULT_CONNECT_TIMEOUT_MILLIS,
              DefaultHttpDataSource.DEFAULT_READ_TIMEOUT_MILLIS,
              true);
    } else {
      dataSourceFactory = new DefaultDataSourceFactory(context, userAg);
    }

    MediaSource mediaSource = buildMediaSource(uri, dataSourceFactory, formatHint, context);
    exoPlayer.setMediaSource(mediaSource);
    exoPlayer.prepare();

    setupVideoPlayer(eventChannel, textureEntry);
  }

  private static boolean isHTTP(Uri uri) {
    if (uri == null || uri.getScheme() == null) {
      return false;
    }
    String scheme = uri.getScheme();
    return scheme.equals("http") || scheme.equals("https");
  }

  private MediaSource buildMediaSource(
      Uri uri, DataSource.Factory mediaDataSourceFactory, String formatHint, Context context) {
    int type;
    if (formatHint == null) {
      type = Util.inferContentType(uri.getLastPathSegment());
    } else {
      switch (formatHint) {
        case FORMAT_SS:
          type = C.TYPE_SS;
          break;
        case FORMAT_DASH:
          type = C.TYPE_DASH;
          break;
        case FORMAT_HLS:
          type = C.TYPE_HLS;
          break;
        case FORMAT_OTHER:
          type = C.TYPE_OTHER;
          break;
        default:
          type = -1;
          break;
      }
    }
    switch (type) {
      case C.TYPE_SS:
        return new SsMediaSource.Factory(
                new DefaultSsChunkSource.Factory(mediaDataSourceFactory),
                new DefaultDataSourceFactory(context, null, mediaDataSourceFactory))
            .createMediaSource(MediaItem.fromUri(uri));
      case C.TYPE_DASH:
        return new DashMediaSource.Factory(
                new DefaultDashChunkSource.Factory(mediaDataSourceFactory),
                new DefaultDataSourceFactory(context, null, mediaDataSourceFactory))
            .createMediaSource(MediaItem.fromUri(uri));
      case C.TYPE_HLS:
        return new HlsMediaSource.Factory(mediaDataSourceFactory)
            .createMediaSource(MediaItem.fromUri(uri));
      case C.TYPE_OTHER:
        return new ProgressiveMediaSource.Factory(mediaDataSourceFactory)
            .createMediaSource(MediaItem.fromUri(uri));
      default:
        {
          throw new IllegalStateException("Unsupported type: " + type);
        }
    }
  }

  private void setupVideoPlayer(
      EventChannel eventChannel, TextureRegistry.SurfaceTextureEntry textureEntry) {

    eventChannel.setStreamHandler(
        new EventChannel.StreamHandler() {
          @Override
          public void onListen(Object o, EventChannel.EventSink sink) {
            eventSink.setDelegate(sink);
          }

          @Override
          public void onCancel(Object o) {
            eventSink.setDelegate(null);
          }
        });

    surface = new Surface(textureEntry.surfaceTexture());
    exoPlayer.setVideoSurface(surface);
    setAudioAttributes(exoPlayer, options.mixWithOthers);

    exoPlayer.addListener(
        new EventListener() {
          private boolean isBuffering = false;

          public void setBuffering(boolean buffering) {
            if (isBuffering != buffering) {
              isBuffering = buffering;
              Map<String, Object> event = new HashMap<>();
              event.put("event", isBuffering ? "bufferingStart" : "bufferingEnd");
              eventSink.success(event);
            }
          }

          @Override
          public void onPlaybackStateChanged(final int playbackState) {
            if (playbackState == Player.STATE_BUFFERING) {
              setBuffering(true);
              sendBufferingUpdate();
            } else if (playbackState == Player.STATE_READY) {
              if (!isInitialized) {
                isInitialized = true;
                sendInitialized();
              }
            } else if (playbackState == Player.STATE_ENDED) {
              Map<String, Object> event = new HashMap<>();
              event.put("event", "completed");
              eventSink.success(event);
            }

            if (playbackState != Player.STATE_BUFFERING) {
              setBuffering(false);
            }
          }

          @Override
          public void onPlayerError(final ExoPlaybackException error) {
            setBuffering(false);
            if (eventSink != null) {
              eventSink.error("VideoError", "Video player had error " + error, null);
            }
          }
        });
  }

  void sendBufferingUpdate() {
    Map<String, Object> event = new HashMap<>();
    event.put("event", "bufferingUpdate");
    List<? extends Number> range = Arrays.asList(0, exoPlayer.getBufferedPosition());
    // iOS supports a list of buffered ranges, so here is a list with a single range.
    event.put("values", Collections.singletonList(range));
    eventSink.success(event);
  }

  @SuppressWarnings("deprecation")
  private static void setAudioAttributes(SimpleExoPlayer exoPlayer, boolean isMixMode) {
    exoPlayer.setAudioAttributes(
        new AudioAttributes.Builder().setContentType(C.CONTENT_TYPE_MOVIE).build(), !isMixMode);
  }

  void play() {
    exoPlayer.setPlayWhenReady(true);
  }

  void pause() {
    exoPlayer.setPlayWhenReady(false);
  }

  void setLooping(boolean value) {
    exoPlayer.setRepeatMode(value ? REPEAT_MODE_ALL : REPEAT_MODE_OFF);
  }

  void setVolume(double value) {
    float bracketedValue = (float) Math.max(0.0, Math.min(1.0, value));
    exoPlayer.setVolume(bracketedValue);
  }

  @SuppressWarnings("unchecked")
  ArrayList getAudios() {
    MappingTrackSelector.MappedTrackInfo mappedTrackInfo =
            trackSelector.getCurrentMappedTrackInfo();

    ArrayList audios = new ArrayList();

    if(mappedTrackInfo == null){
      return audios;
    }

    for(int i =0;i<mappedTrackInfo.getRendererCount();i++)
    {
      if(mappedTrackInfo.getRendererType(i)!= C.TRACK_TYPE_AUDIO)
        continue;

      TrackGroupArray trackGroupArray = mappedTrackInfo.getTrackGroups(i);
      for(int j =0;j<trackGroupArray.length;j++) {

        TrackGroup group = trackGroupArray.get(j);
        TrackNameProvider provider = new DefaultTrackNameProvider(context.getResources());
        for (int k = 0; k < group.length; k++) {
          //if ((mappedTrackInfo.getTrackSupport(i, j, k) &0b111)
          //        == RendererCapabilities.FORMAT_HANDLED) {
            //trackSelector.setParameters(builder);
          //  audios.add(provider.getTrackName(group.getFormat(k)));

          
          //}
               int trackSupport = mappedTrackInfo.getTrackSupport(i, j, k);
               if ((trackSupport & RendererCapabilities.FORMAT_SUPPORT_MASK) == RendererCapabilities.FORMAT_SUPPORT_SUPPORTED) {
                    audios.add(provider.getTrackName(group.getFormat(k)));
                }
        }
      }

    }
    return audios;


  }

  void setAudio( String audioName) {
    MappingTrackSelector.MappedTrackInfo mappedTrackInfo =
            trackSelector.getCurrentMappedTrackInfo();

    StringBuilder str = new StringBuilder();

    for (int i = 0; i < mappedTrackInfo.getRendererCount(); i++) {
      if (mappedTrackInfo.getRendererType(i) != C.TRACK_TYPE_AUDIO)
        continue;

      TrackGroupArray trackGroupArray = mappedTrackInfo.getTrackGroups(i);
      for (int j = 0; j < trackGroupArray.length; j++) {

        TrackGroup group = trackGroupArray.get(j);
        TrackNameProvider provider = new DefaultTrackNameProvider(context.getResources());
        for (int k = 0; k < group.length; k++) {

          if (provider.getTrackName(group.getFormat(k)).equals(audioName)) {

            DefaultTrackSelector.ParametersBuilder builder = trackSelector.getParameters().buildUpon();
            builder.clearSelectionOverrides(i).setRendererDisabled(i, false);
            int[] tracks = {k};
            DefaultTrackSelector.SelectionOverride override = new DefaultTrackSelector.SelectionOverride(j, tracks);
            builder.setSelectionOverride(i, mappedTrackInfo.getTrackGroups(i), override);
            trackSelector.setParameters(builder);
            return ;



          }

        }
      }

    }
  }

  void setAudioByIndex( int  index) {
    MappingTrackSelector.MappedTrackInfo mappedTrackInfo =
            trackSelector.getCurrentMappedTrackInfo();

    StringBuilder str = new StringBuilder();
    int audioIndex =0;

    for (int i = 0; i < mappedTrackInfo.getRendererCount(); i++) {
      if (mappedTrackInfo.getRendererType(i) != C.TRACK_TYPE_AUDIO)
        continue;

      TrackGroupArray trackGroupArray = mappedTrackInfo.getTrackGroups(i);
      for (int j = 0; j < trackGroupArray.length; j++) {

        TrackGroup group = trackGroupArray.get(j);
        TrackNameProvider provider = new DefaultTrackNameProvider(context.getResources());
        for (int k = 0; k < group.length; k++) {

          if (audioIndex == index) {

            DefaultTrackSelector.ParametersBuilder builder = trackSelector.getParameters().buildUpon();
            builder.clearSelectionOverrides(i).setRendererDisabled(i, false);
            int[] tracks = {k};
            DefaultTrackSelector.SelectionOverride override = new DefaultTrackSelector.SelectionOverride(j, tracks);
            builder.setSelectionOverride(i, mappedTrackInfo.getTrackGroups(i), override);
            trackSelector.setParameters(builder);
            return ;



          }
          audioIndex++;


        }
      }

    }
  }

  void setPlaybackSpeed(double value) {
    // We do not need to consider pitch and skipSilence for now as we do not handle them and
    // therefore never diverge from the default values.
    final PlaybackParameters playbackParameters = new PlaybackParameters(((float) value));

    exoPlayer.setPlaybackParameters(playbackParameters);
  }

  void seekTo(int location) {
    exoPlayer.seekTo(location);
  }

  long getPosition() {
    return exoPlayer.getCurrentPosition();
  }

  @SuppressWarnings("SuspiciousNameCombination")
  private void sendInitialized() {
    if (isInitialized) {
      Map<String, Object> event = new HashMap<>();
      event.put("event", "initialized");
      event.put("duration", exoPlayer.getDuration());

      if (exoPlayer.getVideoFormat() != null) {
        Format videoFormat = exoPlayer.getVideoFormat();
        int width = videoFormat.width;
        int height = videoFormat.height;
        int rotationDegrees = videoFormat.rotationDegrees;
        // Switch the width/height if video was taken in portrait mode
        if (rotationDegrees == 90 || rotationDegrees == 270) {
          width = exoPlayer.getVideoFormat().height;
          height = exoPlayer.getVideoFormat().width;
        }
        event.put("width", width);
        event.put("height", height);
      }
      eventSink.success(event);
    }
  }

  void dispose() {
    if (isInitialized) {
      exoPlayer.stop();
    }
    textureEntry.release();
    eventChannel.setStreamHandler(null);
    if (surface != null) {
      surface.release();
    }
    if (exoPlayer != null) {
      exoPlayer.release();
    }
  }
}
