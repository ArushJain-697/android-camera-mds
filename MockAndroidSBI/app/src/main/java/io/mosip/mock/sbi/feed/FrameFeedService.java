// SAJEEV #50: the SBI Android stream extension. The SBI app keeps its own preview and ALSO publishes frames here.
// Security: signature-level permission (manifest) plus a caller-package allow-list (defence in depth).
// Backpressure: a subscriber must ACK each frame; with 2 frames unacknowledged the service drops frames for that
// subscriber, so a slow client cannot stall the pump (and so cannot stall the SBI preview). MOCK frames only.
package io.mosip.mock.sbi.feed;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.util.Log;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class FrameFeedService extends Service {

    public static final String TAG = "SajeevFeed";
    public static final int MSG_SUBSCRIBE = 1;
    public static final int MSG_UNSUBSCRIBE = 2;
    public static final int MSG_FRAME = 3;
    public static final int MSG_ACK = 4;
    public static final int MSG_STATS = 5;
    public static final String KEY_JPEG = "jpeg";
    public static final String KEY_SEQ = "seq";
    public static final String KEY_NANOS = "nanos";
    public static final String KEY_MOCK = "mock";
    public static final String KEY_FPS = "fps";
    public static final String KEY_SENT = "sent";
    public static final String KEY_DROPPED = "dropped";
    /** Frames allowed in flight per subscriber before frames are dropped for it. */
    static final int MAX_IN_FLIGHT = 2;
    static final Set<String> ALLOWED = new HashSet<>(Arrays.asList(
            "io.mosip.registration_client", "nprime.reg.mocksbi.sbinprimetestapp", "io.sajeev.feedprobe"));

    private static final class Subscriber {
        final Messenger reply;
        int inFlight;
        long sent;
        long dropped;

        Subscriber(Messenger reply) {
            this.reply = reply;
        }
    }

    private final Map<Messenger, Subscriber> subs = new ConcurrentHashMap<>();
    private HandlerThread pumpThread;
    private Handler pump;
    private ReplaySource source;
    private long periodMs = 100;
    private long seq;
    private boolean running;
    private Messenger inbox;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!running) {
                return;
            }
            byte[] jpeg = source.next();
            seq++;
            long nanos = System.nanoTime();
            Iterator<Subscriber> it = subs.values().iterator();
            while (it.hasNext()) {
                Subscriber s = it.next();
                if (s.inFlight >= MAX_IN_FLIGHT) {
                    s.dropped++;
                    continue;
                }
                Message m = Message.obtain(null, MSG_FRAME);
                Bundle b = new Bundle();
                b.putByteArray(KEY_JPEG, jpeg);
                b.putLong(KEY_SEQ, seq);
                b.putLong(KEY_NANOS, nanos);
                b.putBoolean(KEY_MOCK, true);
                m.setData(b);
                try {
                    s.reply.send(m);
                    s.inFlight++;
                    s.sent++;
                } catch (RemoteException e) {
                    it.remove();
                }
            }
            pump.postDelayed(this, periodMs);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        int fps = Math.max(1, Math.min(60, prefs.getInt("feed_replay_fps", 10)));
        periodMs = 1000L / fps;
        source = new ReplaySource(this);
        pumpThread = new HandlerThread("sajeev-feed-pump");
        pumpThread.start();
        pump = new Handler(pumpThread.getLooper());
        inbox = new Messenger(new Handler(Looper.getMainLooper(), this::onMessage));
        Log.i(TAG, "MOCK feed ready: fps=" + fps + " fromFiles=" + source.fromFiles());
    }

    private boolean callerAllowed(int uid) {
        String[] pkgs = getPackageManager().getPackagesForUid(uid);
        if (pkgs == null) {
            return false;
        }
        for (String p : pkgs) {
            if (ALLOWED.contains(p) || getPackageName().equals(p)) {
                return true;
            }
        }
        return false;
    }

    private boolean onMessage(Message msg) {
        if (!callerAllowed(msg.sendingUid)) {
            Log.w(TAG, "message from a package that is not on the allow-list was ignored");
            return true;
        }
        switch (msg.what) {
            case MSG_SUBSCRIBE:
                if (msg.replyTo != null) {
                    subs.put(msg.replyTo, new Subscriber(msg.replyTo));
                    if (!running) {
                        running = true;
                        pump.post(tick);
                    }
                }
                return true;
            case MSG_UNSUBSCRIBE:
                subs.remove(msg.replyTo);
                return true;
            case MSG_ACK:
                for (Subscriber s : subs.values()) {
                    if (s.reply.equals(msg.replyTo) && s.inFlight > 0) {
                        s.inFlight--;
                    }
                }
                return true;
            case MSG_STATS:
                for (Subscriber s : subs.values()) {
                    if (s.reply.equals(msg.replyTo)) {
                        Message m = Message.obtain(null, MSG_STATS);
                        Bundle b = new Bundle();
                        b.putLong(KEY_SENT, s.sent);
                        b.putLong(KEY_DROPPED, s.dropped);
                        b.putInt(KEY_FPS, (int) (1000 / periodMs));
                        m.setData(b);
                        try {
                            s.reply.send(m);
                        } catch (RemoteException ignored) {
                            // subscriber is gone
                        }
                    }
                }
                return true;
            default:
                return false;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return inbox.getBinder();
    }

    @Override
    public void onDestroy() {
        running = false;
        if (pumpThread != null) {
            pumpThread.quitSafely();
        }
        super.onDestroy();
    }
}
