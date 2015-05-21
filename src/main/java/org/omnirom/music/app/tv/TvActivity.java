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

package org.omnirom.music.app.tv;

import android.app.Activity;
import android.app.FragmentManager;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.support.v17.leanback.app.BrowseFragment;
import android.support.v17.leanback.widget.ArrayObjectAdapter;
import android.support.v17.leanback.widget.HeaderItem;
import android.support.v17.leanback.widget.ImageCardView;
import android.support.v17.leanback.widget.ListRow;
import android.support.v17.leanback.widget.ListRowPresenter;
import android.support.v17.leanback.widget.OnItemViewClickedListener;
import android.support.v17.leanback.widget.Presenter;
import android.support.v17.leanback.widget.Row;
import android.support.v17.leanback.widget.RowPresenter;
import android.support.v4.app.ActivityOptionsCompat;
import android.support.v7.graphics.Palette;
import android.util.Log;
import android.view.View;

import org.omnirom.music.api.common.Pair;
import org.omnirom.music.app.R;
import org.omnirom.music.app.adapters.HistoryAdapter;
import org.omnirom.music.framework.ListenLogger;
import org.omnirom.music.framework.PluginsLookup;
import org.omnirom.music.model.Album;
import org.omnirom.music.model.Artist;
import org.omnirom.music.model.Playlist;
import org.omnirom.music.model.Song;
import org.omnirom.music.providers.IMusicProvider;
import org.omnirom.music.providers.ProviderAggregator;
import org.omnirom.music.providers.ProviderConnection;
import org.omnirom.music.providers.ProviderIdentifier;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class TvActivity extends Activity {
    public static final String TAG = "TvActivity";

    private ArrayObjectAdapter mRowsAdapter;
    private Handler mHandler;

    protected BrowseFragment mBrowseFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.tv_browsefragment);

        mHandler = new Handler();

        final FragmentManager fragmentManager = getFragmentManager();
        mBrowseFragment = (BrowseFragment) fragmentManager.findFragmentById(
                R.id.browse_fragment);

        // Set display parameters for the BrowseFragment
        mBrowseFragment.setHeadersState(BrowseFragment.HEADERS_ENABLED);
        mBrowseFragment.setTitle(getString(R.string.app_name));
        mBrowseFragment.setBadgeDrawable(getResources().getDrawable(
                R.mipmap.ic_launcher));

        // Set search interface
        mBrowseFragment.setOnSearchClickedListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(TvActivity.this, TvSearchActivity.class);
                startActivity(intent);
            }
        });
        mBrowseFragment.setSearchAffordanceColor(getResources().getColor(R.color.primary_dark));

        // Setup event listeners
        mBrowseFragment.setOnItemViewClickedListener(new OnItemViewClickedListener() {
            @Override
            public void onItemClicked(Presenter.ViewHolder itemViewHolder, Object item,
                                      RowPresenter.ViewHolder rowViewHolder, Row row) {
                if (item instanceof Album) {
                    Album album = (Album) item;
                    Log.d(TAG, "Opening details of album " + album.getName());

                    int color = getResources().getColor(R.color.primary);
                    if (itemViewHolder.view.getTag() != null && itemViewHolder.view.getTag() instanceof Palette) {
                        color = ((Palette) itemViewHolder.view.getTag()).getDarkVibrantColor(color);
                    }

                    Intent intent = new Intent(TvActivity.this, TvAlbumDetailsActivity.class);
                    intent.putExtra(TvAlbumDetailsActivity.EXTRA_ALBUM, album);
                    intent.putExtra(TvAlbumDetailsActivity.EXTRA_COLOR, color);

                    Bundle bundle = ActivityOptionsCompat.makeSceneTransitionAnimation(
                            TvActivity.this,
                            ((ImageCardView) itemViewHolder.view).getMainImageView(),
                            TvAlbumDetailsActivity.SHARED_ELEMENT_NAME).toBundle();
                    startActivity(intent, bundle);
                } else if (item instanceof Artist) {
                    // TODO
                }
            }
        });

        // Build adapter items
        mHandler.postDelayed(new Runnable() {
            public void run() {
                buildRowsAdapter();
            }
        }, 2000);
    }

    private void buildRowsAdapter() {
        mRowsAdapter = new ArrayObjectAdapter(new ListRowPresenter());

        Random rand = new Random();
        final int TYPE_ALBUM = 0;
        final int TYPE_ARTIST = 1;
        final ProviderAggregator aggregator = ProviderAggregator.getDefault();

        // First row: Recently played (10 items, randomly artist or album)
        ListenLogger logger = new ListenLogger(this);
        List<ListenLogger.LogEntry> logEntries = HistoryAdapter.sortByTime(logger.getEntries());
        ArrayObjectAdapter logEntriesRowAdapter = new ArrayObjectAdapter(new StringPresenter());
        int entriesCount = 0;

        for (ListenLogger.LogEntry logEntry : logEntries) {
            if (entriesCount == 10) break;

            // Get the song information
            Song song = aggregator.retrieveSong(logEntry.getReference(), logEntry.getIdentifier());

            if (song == null) {
                // That song might not exist anymore, keep moving
                continue;
            }

            final int type = rand.nextInt(2);
            if (type == TYPE_ALBUM && song.getAlbum() == null
                    || type == TYPE_ARTIST && song.getArtist() == null) {
                // We don't have the requested track info, so skip it
                continue;
            }

            if (type == TYPE_ALBUM) {
                Album album = aggregator.retrieveAlbum(song.getAlbum(), song.getProvider());
                logEntriesRowAdapter.add(album.getName());
            } else if (type == TYPE_ARTIST) {
                Artist artist = aggregator.retrieveArtist(song.getArtist(), song.getProvider());
                logEntriesRowAdapter.add(artist.getName());
            }
            ++entriesCount;
        }

        // Build Recently Played Leanback item
        HeaderItem header = new HeaderItem(0, "Recently played");
        mRowsAdapter.add(new ListRow(header, logEntriesRowAdapter));

        // Get all the available tracks to build Recommendations
        final List<Pair<String, ProviderIdentifier>> availableReferences = new ArrayList<>();
        final List<ProviderConnection> providers = PluginsLookup.getDefault().getAvailableProviders();

        final List<Playlist> playlists = aggregator.getAllPlaylists();
        for (Playlist p : playlists) {
            Iterator<String> it = p.songs();
            while (it.hasNext()) {
                String ref = it.next();
                Pair<String, ProviderIdentifier> pair = Pair.create(ref, p.getProvider());
                if (!availableReferences.contains(pair)) {
                    availableReferences.add(pair);
                }
            }
        }

        for (ProviderConnection provider : providers) {
            IMusicProvider binder = provider.getBinder();
            if (binder != null) {
                int limit = 50;
                int offset = 0;
                boolean goAhead = true;
                try {
                    while (goAhead) {
                        List<Song> songs = binder.getSongs(offset, limit);
                        if (songs == null) {
                            goAhead = false;
                            continue;
                        }

                        if (songs.size() < limit) {
                            goAhead = false;
                        }

                        offset += songs.size();

                        for (Song song : songs) {
                            Pair<String, ProviderIdentifier> pair = Pair.create(song.getRef(), song.getProvider());
                            if (!availableReferences.contains(pair)) {
                                availableReferences.add(pair);
                            }
                        }
                    }
                } catch (RemoteException ignore) {
                }
            }
        }

        // Randomly generate recommendations
        ArrayObjectAdapter recommendedRowAdapter = new ArrayObjectAdapter(new CardPresenter());
        entriesCount = 0;
        Log.e(TAG, "Songs refs: " + availableReferences.size());

        for (Pair<String, ProviderIdentifier> ref : availableReferences) {
            if (entriesCount == 20) break;

            // Get the song information
            Song song = aggregator.retrieveSong(ref.first, ref.second);

            if (song == null) {
                // That song might not exist anymore, keep moving
                continue;
            }

            final int type = rand.nextInt(2);
            if (type == TYPE_ALBUM && song.getAlbum() == null
                    || type == TYPE_ARTIST && song.getArtist() == null) {
                // We don't have the requested track info, so skip it
                continue;
            }

            if (type == TYPE_ALBUM) {
                Album album = aggregator.retrieveAlbum(song.getAlbum(), song.getProvider());
                recommendedRowAdapter.add(album);
            } else if (type == TYPE_ARTIST) {
                Artist artist = aggregator.retrieveArtist(song.getArtist(), song.getProvider());
                recommendedRowAdapter.add(artist);
            }
            ++entriesCount;
        }

        // Build Recommended Leanback item
        header = new HeaderItem(1, "Recommended for you");
        mRowsAdapter.add(new ListRow(header, recommendedRowAdapter));
/*
        header = new HeaderItem(2, "My Library");
        mRowsAdapter.add(new ListRow(header, listRowAdapter));

        header = new HeaderItem(3, "Playlists");
        mRowsAdapter.add(new ListRow(header, listRowAdapter));

        header = new HeaderItem(4, "Settings");
        mRowsAdapter.add(new ListRow(header, listRowAdapter));
*/
        mBrowseFragment.setAdapter(mRowsAdapter);
    }

}