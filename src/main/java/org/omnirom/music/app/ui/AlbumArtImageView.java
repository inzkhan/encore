/*
 * Copyright (C) 2014 Fastboot Mobile, LLC.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of
 * the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See
 * the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program;
 * if not, see <http://www.gnu.org/licenses>.
 */

package org.omnirom.music.app.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;

import org.omnirom.music.app.R;
import org.omnirom.music.app.Utils;
import org.omnirom.music.framework.AlbumArtCache;
import org.omnirom.music.framework.AlbumArtHelper;
import org.omnirom.music.framework.RecyclingBitmapDrawable;
import org.omnirom.music.model.Album;
import org.omnirom.music.model.Artist;
import org.omnirom.music.model.BoundEntity;
import org.omnirom.music.model.Playlist;
import org.omnirom.music.model.Song;
import org.omnirom.music.providers.ProviderAggregator;

/**
 * Square ImageView displaying album art automatically
 */
public class AlbumArtImageView extends SquareImageView implements AlbumArtHelper.AlbumArtListener {
    private final String TAG = "AlbumArtImageView";
    private static final boolean DEBUG = false;
    private static final int DELAY_BEFORE_START = 90;

    private Handler mHandler;
    private OnArtLoadedListener mOnArtLoadedListener;
    private AlbumArtHelper.AlbumArtTask mTask;
    private BoundEntity mRequestedEntity;
    private MaterialTransitionDrawable mDrawable;
    private boolean mCrossfade;
    private boolean mSkipTransition;
    private RecyclingBitmapDrawable mCurrentBitmap;
    private TaskRunnable mRunnable;
    private boolean mCurrentIsDefault;

    private static RecyclingBitmapDrawable sDefaultBitmap;

    public AlbumArtImageView(Context context) {
        super(context);
        initialize();
    }

    public AlbumArtImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initialize();
    }

    public AlbumArtImageView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initialize();
    }

    private void initialize() {
        // Set the placeholder art first-hand
        mHandler = new Handler();
        setScaleType(ScaleType.CENTER_CROP);


        if (sDefaultBitmap == null) {
            sDefaultBitmap = new RecyclingBitmapDrawable(getResources(), ((BitmapDrawable) getContext().getApplicationContext().getResources()
                    .getDrawable(R.drawable.album_placeholder)).getBitmap());
        }

        if (isInEditMode()) {
            setImageDrawable(getResources().getDrawable(R.drawable.album_placeholder));
        } else {
            mDrawable = new MaterialTransitionDrawable(getContext(), sDefaultBitmap);
            setImageDrawable(mDrawable);
        }
    }

    private void freeMemory(boolean removeRequestedEntity) {
        if (mCurrentBitmap != null) {
            mCurrentBitmap = null;
            if (removeRequestedEntity) {
                mRequestedEntity = null;
            }
        }
        if (mTask != null && !mTask.isCancelled()) {
            mTask.cancel(true);
            mTask = null;
        }
    }

    @Override
    protected void finalize() throws Throwable {
        freeMemory(true);
        super.finalize();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (DEBUG) Log.d(TAG, "onDetachedFromWindow: mRequestedEntity=" + mRequestedEntity);
        freeMemory(false);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (DEBUG) Log.d(TAG, "onAttachedToWindow: mRequestedEntity=" + mRequestedEntity);
        if (mRequestedEntity != null && mTask == null && mRunnable == null) {
            BoundEntity ent = mRequestedEntity;
            mRequestedEntity = null;
            loadArtImpl(ent);
        }
    }

    private void forceDrawableReload() {
        // Our drawable is likely changing bounds. From ImageView source code, it seems
        // like there's no way for the drawable to tell the view "hey, I resized", so we
        // manually trigger "updateDrawable()" (a private method in ImageView) by setting
        // the drawable again when we know it changes
        setImageDrawable(null);
        setImageDrawable(mDrawable);
    }

    /**
     * Displays the placeholder album art without transition
     */
    public void setDefaultArt() {
        if (DEBUG) Log.d(TAG, "setDefaultArt: mCurrentBitmap=" + mCurrentBitmap);
        if (mCurrentBitmap != null) {
            mCurrentBitmap = null;
        }

        mDrawable.setImmediateTo(sDefaultBitmap);
        forceDrawableReload();
        mCurrentIsDefault = true;
    }

    /**
     * Sets the listener that will be called when the art is loaded
     * @param listener The listener that will be called
     */
    public void setOnArtLoadedListener(OnArtLoadedListener listener) {
        mOnArtLoadedListener = listener;
    }

    /**
     * When the AlbumArtImageView is in crossfade mode, the album art won't go through the default
     * placeholder state before moving into the next album art, but will crossfade to the next
     * art directly
     * @param crossfade true to enable crossfade mode
     */
    public void setCrossfade(boolean crossfade) {
        mCrossfade = crossfade;
    }

    public void loadArtForSong(final Song song) {
        loadArtImpl(song);
    }

    public void loadArtForAlbum(final Album album) {
        loadArtImpl(album);

        // If it's an album and we're offline and album is unavailable,
        // overlay the offline status thing
        if (ProviderAggregator.getDefault().isOfflineMode()
                && !Utils.isAlbumAvailableOffline(album)) {
            mDrawable.setShowOfflineOverdraw(true);
        } else {
            mDrawable.setShowOfflineOverdraw(false);
        }
    }

    public void loadArtForArtist(final Artist artist) {
        loadArtImpl(artist);
    }

    public void loadArtForPlaylist(final Playlist playlist) {
        loadArtImpl(playlist);
    }

    private void loadArtImpl(final BoundEntity ent) {
        if (ent == null
                || (mRequestedEntity != null
                && !mCurrentIsDefault
                && ent.getRef().equals(mRequestedEntity.getRef()))) {
            // Nothing to do, we are displaying the proper thing already
            return;
        }

        mSkipTransition = false;
        mRequestedEntity = ent;

        if (mTask != null) {
            mTask.cancel(true);
        }
        if (mRunnable != null) {
            mRunnable.cancel();
            mRunnable = null;
        }

        // If we have the image in cache, show it immediately.
        int cacheStatus = AlbumArtCache.getDefault().getCacheStatus(ent);

        // We delay the loading slightly to make sure we don't uselessly load an image that is
        // being quickly flinged through (the requested entity will change in-between as the
        // view is recycled). We don't wait however in crossfade mode as we expect to see the
        // image as soon as possible.
        mRunnable = new TaskRunnable(ent);

        // When we're crossfading, we're assuming we want the image directly
        if (mCrossfade || cacheStatus == AlbumArtCache.CACHE_STATUS_MEMORY) {
            if (cacheStatus != AlbumArtCache.CACHE_STATUS_UNAVAILABLE) {
                mSkipTransition = true;
            }
            setDefaultArt();
            mRunnable.run(true);
        } else {
            setDefaultArt();
            //mHandler.postDelayed(mRunnable, DELAY_BEFORE_START);
            mHandler.post(mRunnable);
        }
    }

    @Override
    public void onArtLoaded(RecyclingBitmapDrawable output, BoundEntity request) {
        if (DEBUG) Log.d(TAG, "onArtLoaded: mCurrentBitmap=" + mCurrentBitmap + " ; output=" + output);

        if (request != mRequestedEntity) {
            if (DEBUG) Log.d(TAG, "onArtLoaded: Too late for " + request.getRef());
            return;
        }

        // If we have an actual result, display it!
        if (output != null) {
            mCurrentBitmap = output;

            if (mSkipTransition) {
                mDrawable.setTransitionDuration(MaterialTransitionDrawable.SHORT_DURATION);
            } else {
                mDrawable.setTransitionDuration(MaterialTransitionDrawable.DEFAULT_DURATION);
            }
            mDrawable.transitionTo(mCurrentBitmap);
            forceDrawableReload();

            if (mOnArtLoadedListener != null) {
                mOnArtLoadedListener.onArtLoaded(this, mCurrentBitmap);
            }

            mCurrentIsDefault = false;
        } else {
            mCurrentIsDefault = true;
        }

        // If it's an album and we're offline and album is unavailable,
        // overlay the offline status thing
        if (request instanceof Album &&
                ProviderAggregator.getDefault().isOfflineMode()
                && !Utils.isAlbumAvailableOffline((Album) request)) {
            mDrawable.setShowOfflineOverdraw(true);
        }

        mRunnable = null;
    }

    public interface OnArtLoadedListener {
        public void onArtLoaded(AlbumArtImageView view, BitmapDrawable drawable);
    }

    private class TaskRunnable implements Runnable {
        private BoundEntity mEntity;
        private boolean mImmediate;

        public TaskRunnable(BoundEntity ent) {
            mEntity = ent;
        }

        public void run(boolean immediate) {
            mImmediate = immediate;
            run();
        }

        @Override
        public void run() {
            if (mRequestedEntity != null && mEntity != null && mRequestedEntity.equals(mEntity)) {
                mTask = AlbumArtHelper.retrieveAlbumArt(getResources(), AlbumArtImageView.this, mEntity, mImmediate);
            }
        }

        public void cancel() {
            mEntity = null;
        }
    }
}
