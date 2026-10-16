package com.example.tunas.ui;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.StateListAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.example.tunas.R;

public final class TunasUi {
    public final Context context;
    public final int cream, surface, olive, forest, sage, muted, error;
    public final Typeface heading, body;

    public TunasUi(Context context) {
        this.context = context;
        cream = context.getColor(R.color.tunas_cream);
        surface = context.getColor(R.color.tunas_surface);
        olive = context.getColor(R.color.tunas_olive);
        forest = context.getColor(R.color.tunas_forest);
        sage = context.getColor(R.color.tunas_sage);
        muted = context.getColor(R.color.tunas_muted);
        error = context.getColor(R.color.tunas_error);
        heading = context.getResources().getFont(R.font.merriweather_sans);
        body = context.getResources().getFont(R.font.signika_negative);
    }

    public int dp(float value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }
    public LinearLayout column() { LinearLayout v = new LinearLayout(context); v.setOrientation(LinearLayout.VERTICAL); return v; }
    public LinearLayout row() { LinearLayout v = new LinearLayout(context); v.setOrientation(LinearLayout.HORIZONTAL); v.setGravity(Gravity.CENTER_VERTICAL); return v; }
    public TextView text(String value, float size, boolean bold) {
        TextView v = new TextView(context);
        v.setText(value); v.setTextSize(size); v.setTextColor(forest);
        v.setTypeface(Typeface.create(bold ? heading : body, bold ? 700 : 400, false));
        v.setIncludeFontPadding(false);
        v.setTextDirection(View.TEXT_DIRECTION_LOCALE);
        return v;
    }
    /** Soft drop shadow the Figma headings and readings carry. */
    public TextView lifted(TextView v) { v.setShadowLayer(dp(3), 0, dp(2), 0x2E000000); return v; }
    public TextView title(String value) { TextView v = lifted(text(value, 24, true)); v.setAccessibilityHeading(true); return v; }
    public TextView caption(String value) { TextView v = text(value, 13, false); v.setTextColor(muted); return v; }
    public void gap(LinearLayout v, int height) { View space = new View(context); v.addView(space, new LinearLayout.LayoutParams(1, dp(height))); }
    public void add(LinearLayout target, View child) {
        android.view.ViewGroup.LayoutParams existing = child.getLayoutParams();
        target.addView(child, existing instanceof LinearLayout.LayoutParams ? existing : new LinearLayout.LayoutParams(-1, -2));
    }
    public void divider(LinearLayout target, int vertical) {
        View line = new View(context); line.setBackgroundColor(0x59A2B595);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, Math.max(1, dp(1))); p.topMargin = p.bottomMargin = dp(vertical);
        target.addView(line, p);
    }
    public void pad(View v, int amount) { v.setPadding(dp(amount), dp(amount), dp(amount), dp(amount)); }
    public GradientDrawable shape(int color, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d;
    }
    public GradientDrawable outlined(int color, int radius, int stroke) { GradientDrawable d = shape(color, radius); d.setStroke(dp(1.5f), stroke); return d; }
    public void card(View v, int radius, int elevation) { v.setBackground(shape(surface, radius)); v.setElevation(dp(elevation)); }
    public void clickable(View v, int color, int radius, Runnable click) { clickable(v, shape(color, radius), radius, click); }
    public void clickable(View v, Drawable base, int radius, Runnable click) {
        v.setBackground(new RippleDrawable(ColorStateList.valueOf(0x26454D39), base, shape(0xFFFFFFFF, radius)));
        v.setFocusable(true); v.setOnClickListener(ignored -> click.run()); press(v);
    }
    /** Small press-in scale; native touch feedback only, nothing loops. */
    public void press(View v) {
        StateListAnimator states = new StateListAnimator();
        states.addState(new int[]{android.R.attr.state_pressed}, scale(v, .97f, 90));
        states.addState(new int[0], scale(v, 1f, 160));
        v.setStateListAnimator(states);
    }
    private AnimatorSet scale(View v, float to, long ms) {
        AnimatorSet set = new AnimatorSet();
        set.playTogether(ObjectAnimator.ofFloat(v, View.SCALE_X, to), ObjectAnimator.ofFloat(v, View.SCALE_Y, to));
        set.setDuration(ms); return set;
    }
    public Button button(String label, boolean primary, Runnable click) {
        Button v = new Button(context); v.setText(label); v.setTextSize(16); v.setAllCaps(false);
        v.setTypeface(Typeface.create(heading, 700, false));
        v.setTextColor(primary ? cream : olive); v.setMinHeight(dp(52)); v.setMinimumHeight(dp(52));
        v.setPadding(dp(16), dp(12), dp(16), dp(12));
        clickable(v, primary ? shape(olive, 14) : outlined(cream, 14, olive), 14, click);
        if (primary) v.setElevation(dp(3));
        return v;
    }
    /** Compact pill used for in-page back navigation instead of a full-width bar. */
    public Button back(Runnable click) {
        Button v = button("‹  Back", false, click); v.setContentDescription("Back");
        v.setTextSize(15); v.setPadding(dp(18), 0, dp(20), 0); v.setMinWidth(0); v.setMinimumWidth(0);
        clickable(v, surface, 40, click); v.setElevation(dp(2));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, dp(48)); p.gravity = Gravity.START; v.setLayoutParams(p);
        return v;
    }
    /** Tinted icon on the leading edge of a button or label. */
    public void lead(TextView v, int resource, int tint, int size) {
        Drawable icon = context.getDrawable(resource).mutate(); icon.setTint(tint); icon.setBounds(0, 0, dp(size), dp(size));
        v.setCompoundDrawablesRelative(icon, null, null, null); v.setCompoundDrawablePadding(dp(10));
    }
    /** Borderless round icon button with a 48dp touch target. */
    public ImageView iconButton(int resource, String description, Runnable click) {
        ImageView v = image(resource); v.setColorFilter(forest); v.setContentDescription(description);
        v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES); v.setPadding(dp(14), dp(14), dp(14), dp(14));
        v.setBackground(new RippleDrawable(ColorStateList.valueOf(0x26454D39), null, shape(0xFFFFFFFF, 24)));
        v.setFocusable(true); v.setClickable(true); v.setOnClickListener(ignored -> click.run());
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(48), dp(48))); return v;
    }
    /** Settings-style list row: icon, label, chevron. */
    public LinearLayout listRow(int icon, String label, Runnable click) {
        LinearLayout v = row(); v.setPadding(dp(12), dp(6), dp(12), dp(6)); v.setMinimumHeight(dp(56));
        ImageView glyph = image(icon); glyph.setColorFilter(forest); glyph.setPadding(dp(8), dp(8), dp(8), dp(8));
        glyph.setBackground(shape(0x4DA2B595, 12)); v.addView(glyph, new LinearLayout.LayoutParams(dp(38), dp(38)));
        TextView name = text(label, 16, true); name.setPadding(dp(14), 0, dp(8), 0);
        v.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        TextView chevron = text("›", 26, false); chevron.setTextColor(muted); v.addView(chevron);
        v.setContentDescription(label);
        clickable(v, 0x00000000, 12, click); return v;
    }
    public EditText input(String hint, int type) {
        EditText v = new EditText(context); v.setTextSize(17); v.setTypeface(body);
        switch (hint) {
            case "Full name": v.setId(R.id.full_name); break;
            case "Email": case "you@example.com": v.setId(R.id.email); break;
            case "Plant nickname": v.setId(R.id.plant_nickname); break;
            case "Days between checks": v.setId(R.id.water_interval); break;
            case "e.g. Living Room": v.setId(R.id.room_name); break;
            case "Search plants": v.setId(R.id.plant_search); break;
        }
        v.setTextColor(forest); v.setHintTextColor(muted); v.setHint(hint); v.setInputType(type);
        v.setSingleLine(true); v.setMinimumHeight(dp(52));
        v.setPadding(dp(16), dp(12), dp(16), dp(12));
        StateListDrawable field = new StateListDrawable();
        GradientDrawable focused = shape(0xFFF7F6E4, 14); focused.setStroke(dp(2), forest);
        GradientDrawable idle = shape(0xFFF7F6E4, 14); idle.setStroke(dp(1), sage);
        field.addState(new int[]{android.R.attr.state_focused}, focused); field.addState(new int[0], idle);
        v.setBackground(field);
        v.setSelectAllOnFocus(false); return v;
    }
    public ImageView image(int resource) {
        ImageView image = new ImageView(context); image.setImageResource(resource);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return image;
    }
    public void logo(LinearLayout column) {
        ImageView logo = image(R.drawable.figma_wordmark); logo.setContentDescription("Tunas");
        logo.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(228), dp(55));
        p.gravity = Gravity.CENTER_HORIZONTAL; p.topMargin = dp(24); p.bottomMargin = dp(32);
        column.addView(logo, p);
    }
}
