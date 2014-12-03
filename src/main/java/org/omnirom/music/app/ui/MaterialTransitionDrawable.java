
package org.omnirom.music.app.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.view.animation.AccelerateDecelerateInterpolator;

import org.omnirom.music.app.R;
import org.omnirom.music.framework.RecyclingBitmapDrawable;

/**
 * <p>
 * Class that allows drawable transitions in a way that fits Google's Material Design specifications
 * (see <a href="http://www.google.com/design/spec/patterns/imagery-treatment.html">Material Design
 * pattern 'Imagery Treatment'</a>).
 * </p>
 */
public class MaterialTransitionDrawable extends Drawable {
    public static final long DEFAULT_DURATION = 1000;
    public static final long SHORT_DURATION = 300;

    private RecyclingBitmapDrawable mBaseDrawable;
    private RecyclingBitmapDrawable mTargetDrawable;
    private BitmapDrawable mOfflineDrawable;
    private final AccelerateDecelerateInterpolator mInterpolator;
    private long mStartTime;
    private boolean mAnimating;
    private long mDuration = DEFAULT_DURATION;
    private ColorMatrix mColorMatSaturation;
    private Paint mPaint;
    private boolean mShowOfflineOverdraw;
    private long mOfflineStartTime;


    public MaterialTransitionDrawable(Context ctx, RecyclingBitmapDrawable base) {
        this(ctx);
        mBaseDrawable = base;
        invalidateSelf();
    }

    public MaterialTransitionDrawable(Context ctx) {
        mInterpolator = new AccelerateDecelerateInterpolator();
        mAnimating = false;
        mShowOfflineOverdraw = false;
        mColorMatSaturation = new ColorMatrix();
        mPaint = new Paint();
        mOfflineDrawable = (BitmapDrawable) ctx.getResources().getDrawable(R.drawable.ic_cloud_offline);
    }

    public BitmapDrawable getFinalDrawable() {
        if (mTargetDrawable != null) {
            return mTargetDrawable;
        } else {
            return mBaseDrawable;
        }
    }

    public void setTransitionDuration(long durationMillis) {
        mDuration = durationMillis;
    }

    public void setImmediateTo(RecyclingBitmapDrawable drawable) {
        // Cancel animation
        mAnimating = false;
        mTargetDrawable = null;
        mShowOfflineOverdraw = false;

        // Set new drawable as base and draw it
        if (mBaseDrawable != null) {
            mBaseDrawable.setIsDisplayed(false);
        }

        mBaseDrawable = drawable;
        mBaseDrawable.setBounds(getBounds());
        mBaseDrawable.setIsDisplayed(true);
        invalidateSelf();
    }

    public void transitionTo(final RecyclingBitmapDrawable drawable) {
        if (drawable != mTargetDrawable) {
            mTargetDrawable = drawable;
            mTargetDrawable.setBounds(getBounds());

            mStartTime = -1;
            mAnimating = true;
        }
    }

    public void setShowOfflineOverdraw(boolean show) {
        if (mShowOfflineOverdraw != show) {
            mShowOfflineOverdraw = show;
            mOfflineStartTime = SystemClock.uptimeMillis();
            invalidateSelf();
        }
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);

        if (mBaseDrawable != null & !mAnimating) {
            mBaseDrawable.setBounds(bounds);
        }
        if (mTargetDrawable != null) {
            mTargetDrawable.setBounds(bounds);
        }
    }

    @Override
    public void draw(Canvas canvas) {
        if (mAnimating) {
            if (mStartTime < 0) {
                mStartTime = SystemClock.uptimeMillis();
            }

            final float rawProgress = Math.min(1.0f,
                    ((float) (SystemClock.uptimeMillis() - mStartTime)) / ((float) mDuration));

            // As per the Material Design spec, animation goes into 3 steps. Ranging from 0 to 100,
            // opacity is full at 50, exposure (gamma + black output) at 75, and saturation at 100.
            // For performance, we only do the saturation and opacity transition
            final float inputOpacity = Math.min(1.0f, rawProgress * (1.0f / 0.5f));
            // final float inputExposure = Math.min(1.0f, rawProgress * (1.0f / 0.75f));

            final float progressOpacity = mInterpolator.getInterpolation(inputOpacity);
            // final float progressExposure = 1.0f - mInterpolator.getInterpolation(inputExposure);
            final float progressSaturation = mInterpolator.getInterpolation(rawProgress);

            if (mBaseDrawable != null) {
                drawTranslatedBase(canvas);
            }

            mColorMatSaturation.setSaturation(progressSaturation);
            ColorMatrixColorFilter colorMatFilter = new ColorMatrixColorFilter(mColorMatSaturation);
            mPaint.setAlpha((int) (progressOpacity * 255.0f));
            mPaint.setColorFilter(colorMatFilter);

            if (!mTargetDrawable.getBitmap().isRecycled()) {
                canvas.drawBitmap(mTargetDrawable.getBitmap(), 0, 0, mPaint);
            }

            if (rawProgress >= 1.0f) {
                mAnimating = false;
                if (mBaseDrawable != null) {
                    mBaseDrawable.setIsDisplayed(false);
                }
                mBaseDrawable = mTargetDrawable;
            } else {
                invalidateSelf();
            }
        } else if (mBaseDrawable != null) {
            if (!mBaseDrawable.getBitmap().isRecycled()) {
                mBaseDrawable.draw(canvas);
            }
        }

        if (mShowOfflineOverdraw) {
            int alpha = (int) Math.min(160, (SystemClock.uptimeMillis() - mOfflineStartTime) / 4);
            canvas.drawColor(0x00888888 | ((alpha & 0xFF) << 24));

            mPaint.setAlpha(alpha * 255 / 160);
            canvas.drawBitmap(mOfflineDrawable.getBitmap(),
                    getBounds().centerX() - mOfflineDrawable.getIntrinsicWidth() / 2,
                    getBounds().centerY() - mOfflineDrawable.getIntrinsicHeight() / 2,
                    mPaint);
            mOfflineDrawable.draw(canvas);

            if (alpha != 160) {
                invalidateSelf();
            }
        }
    }

    private void drawTranslatedBase(Canvas canvas) {
        // Pad the base drawable to be at the center of the target size
        final float targetWidth = mTargetDrawable.getIntrinsicWidth();
        final float targetHeight = mTargetDrawable.getIntrinsicHeight();
        Rect baseBounds = mBaseDrawable.getBounds();
        final float baseWidth = baseBounds.width();
        final float baseHeight = baseBounds.height();

        canvas.save();

        float scaling = Math.min(targetWidth / baseWidth, targetHeight / baseHeight);

        final float scaledBaseWidth = scaling * baseWidth;
        final float scaledBaseHeight = scaling * baseHeight;

        canvas.translate((targetWidth - scaledBaseWidth) * 0.5f,
                (targetHeight - scaledBaseHeight) * 0.5f);
        canvas.scale(scaling, scaling);

        if (!mBaseDrawable.getBitmap().isRecycled()) {
            mBaseDrawable.draw(canvas);
        }
        canvas.restore();
    }

    @Override
    public int getIntrinsicHeight() {
        if (mAnimating && mTargetDrawable != null) {
            return mTargetDrawable.getIntrinsicHeight();
        } else if (mBaseDrawable != null) {
            return mBaseDrawable.getIntrinsicHeight();
        } else {
            return super.getIntrinsicHeight();
        }
    }

    @Override
    public int getIntrinsicWidth() {
        if (mAnimating && mTargetDrawable != null) {
            return mTargetDrawable.getIntrinsicWidth();
        } else if (mBaseDrawable != null) {
            return mBaseDrawable.getIntrinsicWidth();
        } else {
            return super.getIntrinsicWidth();
        }
    }

    @Override
    public void setAlpha(int i) {

    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {

    }

    @Override
    public int getOpacity() {
        return 255;
    }
}