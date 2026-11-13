package com.example.tunas.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.view.View;
import com.example.tunas.R;
import com.example.tunas.data.SensorHistory;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Line chart of one sensor reading over a time window. Silences longer than GAP_MILLIS stay empty. */
public final class HistoryChart extends View {
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG), fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG), label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG), empty = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path stroke = new Path(), area = new Path();
    private final Date stamp = new Date();
    private final float density;
    private List<SensorHistory.Sample> samples = Collections.emptyList();
    private int metric;
    private long from, to = 1;
    private boolean percent;
    private DateFormat axis = DateFormat.getTimeInstance(DateFormat.SHORT);

    public HistoryChart(Context context) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        int forest = context.getColor(R.color.tunas_forest), muted = context.getColor(R.color.tunas_muted);
        line.setColor(forest); line.setStyle(Paint.Style.STROKE); line.setStrokeWidth(2.5f * density);
        line.setStrokeJoin(Paint.Join.ROUND); line.setStrokeCap(Paint.Cap.ROUND);
        grid.setColor(0x8CA2B595); grid.setStrokeWidth(density);
        grid.setPathEffect(new DashPathEffect(new float[]{6 * density, 6 * density}, 0)); // same dashed grid as the background
        label.setColor(muted); label.setTextSize(11 * density); label.setTypeface(context.getResources().getFont(R.font.signika_negative));
        empty.set(label); empty.setTextSize(14 * density); empty.setTextAlign(Paint.Align.CENTER);
        dot.setColor(forest);
    }

    /** {@code percent} pins the axis to 0-100 so a flat moisture line doesn't look like a big swing. */
    public void show(List<SensorHistory.Sample> samples, int metric, long from, long to, boolean percent, boolean week) {
        this.samples = samples; this.metric = metric; this.from = from; this.to = Math.max(from + 1, to); this.percent = percent;
        axis = week ? new SimpleDateFormat("EEE d", Locale.getDefault()) : DateFormat.getTimeInstance(DateFormat.SHORT);
        invalidate();
    }

    @Override protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        fill.setShader(new LinearGradient(0, 0, 0, h, 0x66A2B595, 0x00A2B595, Shader.TileMode.CLAMP));
    }

    @Override protected void onDraw(Canvas canvas) {
        float left = 34 * density, right = getWidth() - 10 * density, top = 10 * density, bottom = getHeight() - 26 * density;
        double low = percent ? 0 : Double.MAX_VALUE, high = percent ? 100 : -Double.MAX_VALUE;
        if (!percent) for (SensorHistory.Sample s : samples) {
            double v = s.values[metric];
            if (!Double.isNaN(v)) { low = Math.min(low, v); high = Math.max(high, v); }
        }
        if (low > high) { low = 0; high = 1; }
        if (high - low < 1) { low -= 1; high += 1; }
        if (!percent) { double pad = (high - low) * 0.1; low -= pad; high += pad; }

        label.setTextAlign(Paint.Align.RIGHT);
        for (int i = 0; i <= 2; i++) {
            float y = top + (bottom - top) * i / 2f;
            canvas.drawLine(left, y, right, y, grid);
            canvas.drawText(String.format(Locale.getDefault(), "%.0f", high - (high - low) * i / 2), left - 6 * density, y + 4 * density, label);
        }
        label.setTextAlign(Paint.Align.LEFT);
        stamp.setTime(from); canvas.drawText(axis.format(stamp), left, getHeight() - 6 * density, label);
        label.setTextAlign(Paint.Align.RIGHT);
        stamp.setTime(to); canvas.drawText(axis.format(stamp), right, getHeight() - 6 * density, label);

        stroke.reset(); area.reset();
        boolean pen = false, drew = false;
        long previous = 0; float x = 0, y = 0;
        for (SensorHistory.Sample s : samples) {
            double v = s.values[metric];
            // Break the line instead of bridging hours the app was closed or the sensor was offline.
            if (pen && (Double.isNaN(v) || s.time - previous > SensorHistory.GAP_MILLIS)) { area.lineTo(x, bottom); area.close(); pen = false; }
            if (Double.isNaN(v) || s.time < from || s.time > to) continue;
            x = left + (right - left) * (s.time - from) / (float) (to - from);
            y = (float) (bottom - (bottom - top) * (v - low) / (high - low));
            if (pen) { stroke.lineTo(x, y); area.lineTo(x, y); }
            else { stroke.moveTo(x, y); area.moveTo(x, bottom); area.lineTo(x, y); pen = true; }
            previous = s.time; drew = true;
        }
        if (pen) { area.lineTo(x, bottom); area.close(); }
        if (!drew) { canvas.drawText("No readings in this period yet", (left + right) / 2, (top + bottom) / 2, empty); return; }
        canvas.drawPath(area, fill);
        canvas.drawPath(stroke, line);
        canvas.drawCircle(x, y, 4 * density, dot); // latest reading
    }
}
