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

package fabiogentile.powertutor.util;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import com.rvalerio.fgchecker.AppChecker;

import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

/**
 * This detector looks for transitions where one app leaves the foreground and
 * another enters the foreground to detect apps that are legitimately in the
 * foreground.  If no application is known to be legitimate system is returned.
 */
public class ForegroundDetector {
    private static final String TAG = "ForegroundDetector";
    private int lastSize;
    private int[] lastUids;
    private int nowSize;
    private int[] nowUids;
    private Context context;

    private PackageManager pm;
    private List<ApplicationInfo> packages;



    private BitSet validated;

    private ActivityManager activityManager;

    public ForegroundDetector(ActivityManager activityManager, Context context) {
        lastSize = nowSize = 0;
        lastUids = new int[10];
        nowUids = new int[10];
        validated = new BitSet(1 << 16);
        validated.set(android.os.Process.myUid());
        this.activityManager = activityManager;
        this.context = context;
        this.pm = context.getPackageManager();
        this.packages = pm.getInstalledApplications(PackageManager.GET_META_DATA);
    }


    /**
     * Figure out what uid should be charged for screen usage.
     *
     * @return uid of the app in foreground
     */
    public int getForegroundUid() {
        int ret = SystemInfo.AID_SYSTEM;
        String appName = "SYSTEM";

        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            AppChecker appChecker = new AppChecker();
            appName = appChecker.getForegroundApp(context);

            /* HACK
             * When the user is in the home screen result of getForegroundApp is
             * com.google.android.googlequicksearchbox instead of com.android.systemui
             */
            if (appName.equals("com.google.android.googlequicksearchbox")) //HACK
                appName = "com.android.systemui";

            //Get UID from packageName
            for (ApplicationInfo packageInfo : packages) {
                if (packageInfo.packageName.equals(appName)) {
                    //get the UID for the selected app
                    ret = packageInfo.uid;
                    break; //found a match, don't need to search anymore
                }
            }
        } else {
            appName = "";
            SystemInfo sysInfo = SystemInfo.getInstance();
            List<ActivityManager.RunningAppProcessInfo> appProcs = activityManager.getRunningAppProcesses();

            // Move the last iteration to last and resize the other array if needed.
            int[] tmp = lastUids;
            lastUids = nowUids;
            lastSize = nowSize;
            if (tmp.length < appProcs.size()) {
                tmp = new int[appProcs.size()];
            }
            nowUids = tmp;

            // Fill in the uids from appProcs.
            nowSize = 0;
            for (ActivityManager.RunningAppProcessInfo app : appProcs) {
                Log.v(TAG, "getForegroundUid: " + app.processName + " ");
                if (app.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                    int uid = sysInfo.getUidForPid(app.pid);
                    if (SystemInfo.AID_APP <= uid && uid < 1 << 16) {
                        nowUids[nowSize++] = uid;
                    }
                }
            }
            Arrays.sort(nowUids, 0, nowSize);

            // Find app-exit app-enter transitions.
            int appExit = -1;
            int appEnter = -1;
            int indNow = 0;
            int indLast = 0;
            while (indNow < nowSize && indLast < lastSize) {
                if (nowUids[indNow] == lastUids[indLast]) {
                    indNow++;
                    indLast++;
                } else if (nowUids[indNow] < lastUids[indLast]) {
                    appEnter = nowUids[indNow++];
                } else {
                    appExit = lastUids[indLast++];
                }
            }
            if (indNow < nowSize) appEnter = nowUids[indNow];
            if (indLast < lastSize) appExit = lastUids[indLast];

            // Found an interesting transition.  Validate both applications.
            if (appEnter != -1 && appExit != -1) {
                validated.set(appEnter);
                validated.set(appExit);
            }

            // Now find a valid application now.  Hopefully there is only one.  If there
            // are none return system.  If there are several return the one with the
            // highest uid.
            for (int i = nowSize - 1; i >= 0; i--) {
                if (validated.get(nowUids[i])) {
                    ret = nowUids[i];
                }
            }
        }
        Log.v(TAG, "getForegroundUid: " + appName + "(" + ret + ")");
        return ret;
    }
}
