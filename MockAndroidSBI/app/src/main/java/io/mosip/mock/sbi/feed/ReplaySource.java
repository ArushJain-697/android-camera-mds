// SAJEEV #50: frame source for the feed. MOCK. Replays JPEG files from files/replay/, or SYNTHETIC frames if none exist.
package io.mosip.mock.sbi.feed;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;

/** Produces JPEG frames in a loop. Not a camera. */
final class ReplaySource {

    private final File[] files;
    private int index;
    private long seq;

    ReplaySource(Context context) {
        File dir = new File(context.getFilesDir(), "replay");
        File[] found = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".jpg"));
        if (found != null) {
            Arrays.sort(found);
        }
        this.files = found == null ? new File[0] : found;
    }

    /** @return true when frames come from files, false when they are synthetic */
    boolean fromFiles() {
        return files.length > 0;
    }

    /** @return the next frame as JPEG bytes */
    byte[] next() {
        try {
            if (files.length > 0) {
                byte[] b = Files.readAllBytes(files[index].toPath());
                index = (index + 1) % files.length;
                seq++;
                return b;
            }
        } catch (Exception e) {
            // fall through to a synthetic frame
        }
        seq++;
        return synthetic(seq);
    }

    /** SYNTHETIC: a 160x120 frame showing its own sequence number. */
    static byte[] synthetic(long seq) {
        Bitmap bmp = Bitmap.createBitmap(160, 120, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(Color.rgb((int) (seq * 7 % 255), 90, 140));
        Paint p = new Paint();
        p.setColor(Color.WHITE);
        p.setTextSize(28f);
        c.drawText("MOCK " + seq, 12f, 64f, p);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.JPEG, 70, out);
        bmp.recycle();
        return out.toByteArray();
    }
}
