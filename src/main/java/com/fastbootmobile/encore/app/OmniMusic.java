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

package com.fastbootmobile.encore.app;

import android.app.Application;
import android.net.http.HttpResponseCache;
import android.os.Build;
import android.util.Log;

import com.joshdholtz.sentry.Sentry;

import org.json.JSONException;
import org.json.JSONObject;
import com.fastbootmobile.encore.api.echonest.AutoMixManager;
import com.fastbootmobile.encore.art.ImageCache;
import com.fastbootmobile.encore.framework.PluginsLookup;
import com.fastbootmobile.encore.providers.ProviderAggregator;

import java.io.File;
import java.io.IOException;

import uk.co.chrisjenx.calligraphy.CalligraphyConfig;

/**
 * Application structure wrapper to handle various application-wide (activity + services) properties
 */
public class OmniMusic extends Application {
    private static final String TAG = "OmniMusic";

    @Override
    public void onCreate() {
        super.onCreate();

        Sentry.setCaptureListener(new Sentry.SentryEventCaptureListener() {
            @Override
            public Sentry.SentryEventBuilder beforeCapture(Sentry.SentryEventBuilder sentryEventBuilder) {
                JSONObject tags = sentryEventBuilder.getTags();
                try {
                    tags.put("OS", "Android " + Build.VERSION.RELEASE);
                    tags.put("OSCodename", Build.VERSION.CODENAME);
                    tags.put("Device", Build.DEVICE);
                    tags.put("Model", Build.MODEL);
                    tags.put("Manufacturer", Build.MANUFACTURER);
                    tags.put("AppVersionCode", String.valueOf(BuildConfig.VERSION_CODE));
                    tags.put("AppVersionName", BuildConfig.VERSION_NAME);
                    tags.put("AppFlavor", BuildConfig.FLAVOR);
                } catch (JSONException e) {
                    Log.e(TAG, "Failed to put a tag into Sentry", e);
                }

                sentryEventBuilder.addModule("app", BuildConfig.VERSION_NAME);
                return sentryEventBuilder;
            }
        });
        Sentry.init(this, "https://sentry.fastboot.mobi/",
                "https://4dc1acbdb1cb423282e2a59f553e1153:9415087b9e1348c3ba4bed44be599f6a@sentry.fastboot.mobi/2");


        // Setup the plugins system
        ProviderAggregator.getDefault().setContext(getApplicationContext());
        PluginsLookup.getDefault().initialize(getApplicationContext());

        /**
         * Note about the cache and EchoNest: The HTTP cache would sometimes cache request
         * we didn't want (such as status query for Taste Profile update). We're using
         * a hacked jEN library that doesn't cache these requests.
         */
        // Setup network cache
        try {
            final File httpCacheDir = new File(getCacheDir(), "http");
            final long httpCacheSize = 100 * 1024 * 1024; // 100 MiB
            final HttpResponseCache cache = HttpResponseCache.install(httpCacheDir, httpCacheSize);

            Log.i(TAG, "HTTP Cache size: " + cache.size() / 1024 / 1024 + "MB");
        } catch (IOException e) {
            Log.w(TAG, "HTTP response cache installation failed", e);
        }

        // Setup image cache
        ImageCache.getDefault().initialize(getApplicationContext());

        // Setup Automix system
        AutoMixManager.getDefault().initialize(getApplicationContext());

        // Setup custom fonts
        CalligraphyConfig.initDefault("fonts/Roboto-Regular.ttf", R.attr.fontPath);
    }
}