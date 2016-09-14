/*
Copyright (C) 2011 The University of Michigan

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Please send inquiries to powertutor@umich.edu
*/

package fabiogentile.powertutor.components;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.provider.Settings;
import android.util.Log;

import java.io.IOException;
import java.io.OutputStreamWriter;

import fabiogentile.powertutor.phone.PhoneConstants;
import fabiogentile.powertutor.service.IterationData;
import fabiogentile.powertutor.service.PowerData;
import fabiogentile.powertutor.util.ForegroundDetector;
import fabiogentile.powertutor.util.Recycler;
import fabiogentile.powertutor.util.SystemInfo;

public class LCD extends PowerComponent {
    private final String TAG = "LCD";
    private Context context;
    private PhoneConstants constants;
    private ForegroundDetector foregroundDetector;
    private BroadcastReceiver broadcastReceiver;
    private boolean screenOn;
    private String brightnessFile;
    private int prevBrightness = 0;

    public LCD(Context context, PhoneConstants constants) {
        this.context = context;
        this.constants = constants;
        screenOn = true;

        if (context == null) {
            return;
        }

        foregroundDetector = new ForegroundDetector((ActivityManager)
                context.getSystemService(Context.ACTIVITY_SERVICE), context);
        broadcastReceiver = new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                synchronized (this) {
                    if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
                        screenOn = false;
                    } else if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
                        screenOn = true;
                    }
                }
            }

        };
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_SCREEN_OFF);
        intentFilter.addAction(Intent.ACTION_SCREEN_ON);
        context.registerReceiver(broadcastReceiver, intentFilter);

        //brightnessFile = constants.backlightFile();
        brightnessFile = null;
    }

    @Override
    protected void onExit() {
        context.unregisterReceiver(broadcastReceiver);
        super.onExit();
    }

    @Override
    public IterationData calculateIteration(long iteration) {
        IterationData result = IterationData.obtain();

        boolean screen;
        synchronized (this) {
            screen = screenOn;
        }

        int brightness = prevBrightness;
        if (brightnessFile != null) {
            brightness = (int) SystemInfo.getInstance().readLongFromFile(brightnessFile);
        } else {
            try {
                brightness = Settings.System.getInt(context.getContentResolver(),
                        Settings.System.SCREEN_BRIGHTNESS);
            } catch (Settings.SettingNotFoundException ex) {
                Log.e(TAG, "Could not retrieve brightness information");
                brightness = prevBrightness;
            }
        }
        if (brightness < 0 || brightness > brightness) {
            Log.w(TAG, "Could not retrieve brightness information");
            brightness = prevBrightness;
        }

        prevBrightness = brightness;

        LcdData data = LcdData.obtain();
        data.init(brightness, screen);
        result.setPowerData(data);

        if (screen) {
            LcdData uidData = LcdData.obtain();
            uidData.init(brightness, screen);
            result.addUidPowerData(foregroundDetector.getForegroundUid(), uidData);
        }

        return result;
    }

    @Override
    public boolean hasUidInformation() {
        return true;
    }

    @Override
    public String getComponentName() {
        return "LCD";
    }

    public static class LcdData extends PowerData {
        private static Recycler<LcdData> recycler = new Recycler<LcdData>();
        public int brightness;
        public boolean screenOn;

        private LcdData() {
        }

        public static LcdData obtain() {
            LcdData result = recycler.obtain();
            if (result != null) return result;
            return new LcdData();
        }

        @Override
        public void recycle() {
            recycler.recycle(this);
        }

        public void init(int brightness, boolean screenOn) {
            this.brightness = brightness;
            this.screenOn = screenOn;
        }

        public void writeLogDataInfo(OutputStreamWriter out) throws IOException {
            StringBuilder res = new StringBuilder();
            res.append("LCD-brightness ").append(brightness)
                    .append("\nLCD-screen-on ").append(screenOn).append("\n");
            out.write(res.toString());
        }
    }
}
