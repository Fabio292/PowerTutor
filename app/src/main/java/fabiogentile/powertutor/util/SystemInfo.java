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
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;
import android.util.SparseArray;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

import fabiogentile.powertutor.service.PowerEstimator;

// TODO: 18/08/16 nelle hashMap facio un clear o una reinserzione?
public class SystemInfo {
    /* Uids as listed in android_filesystem_config.h */
    public static final int AID_ALL = -1;           /* A special constant we will
                                                     * use to indicate a request
                                                     * for global information. */
    public static final int AID_ROOT = 0;           /* traditional unix root user */
    public static final int AID_SYSTEM = 1000;      /* system server */
    public static final int AID_RADIO = 1001;       /* telephony subsystem, RIL */
    public static final int AID_BLUETOOTH = 1002;   /* bluetooth subsystem */
    public static final int AID_GRAPHICS = 1003;    /* graphics devices */
    public static final int AID_INPUT = 1004;       /* input devices */
    public static final int AID_AUDIO = 1005;       /* audio devices */
    public static final int AID_CAMERA = 1006;      /* camera devices */
    public static final int AID_LOG = 1007;         /* log devices */
    public static final int AID_COMPASS = 1008;     /* compass device */
    public static final int AID_MOUNT = 1009;       /* mountd socket */
    public static final int AID_WIFI = 1010;        /* wifi subsystem */
    public static final int AID_ADB = 1011;         /* android debug bridge (adbd) */
    public static final int AID_INSTALL = 1012;     /* group for installing  packages */
    public static final int AID_MEDIA = 1013;       /* mediaserver process */
    public static final int AID_DHCP = 1014;        /* dhcp client */
    public static final int AID_SHELL = 2000;       /* adb and debug shell user */
    public static final int AID_CACHE = 2001;       /* cache access */
    public static final int AID_DIAG = 2002;        /* access to diagnostic resources */
    /* The 3000 series are intended for use as supplemental group id's only.
     * They indicate special Android capabilities that the kernel is aware of. */
    public static final int AID_NET_BT_ADMIN = 3001;/* bluetooth: create any socket */
    public static final int AID_NET_BT = 3002;      /* bluetooth: create sco, rfcomm or l2cap sockets */
    public static final int AID_INET = 3003;        /* can create AF_INET and AF_INET6 sockets */
    public static final int AID_NET_RAW = 3004;     /* can create raw INET sockets */
    public static final int AID_MISC = 9998;        /* access to misc storage */
    public static final int AID_NOBODY = 9999;
    public static final int AID_APP = 10000;        /* first app user */
    /* These are stolen from Process.java which hides these constants. */
    public static final int PROC_SPACE_TERM = (int) ' ';
    public static final int PROC_TAB_TERM = (int) '\t';
    public static final int PROC_LINE_TERM = (int) '\n';
    public static final int PROC_COMBINE = 0x100;
    public static final int PROC_OUT_LONG = 0x2000;
    public static final int PROC_OUT_STRING = 0x1000;
    public static final int INDEX_USER_TIME = 0;
    public static final int INDEX_SYS_TIME = 1;
    public static final int INDEX_TOTAL_TIME = 2;
    public static final int INDEX_MEM_TOTAL = 0;
    public static final int INDEX_MEM_FREE = 1;
    public static final int INDEX_MEM_BUFFERS = 2;
    public static final int INDEX_MEM_CACHED = 3;
    private static final String TAG = "SystemInfo";
    private static final int[] READ_LONG_FORMAT = new int[]{
            PROC_SPACE_TERM | PROC_OUT_LONG
    };
    //<editor-fold desc="PROCESS FORMAT">
    private static final int[] PROCESS_STATS_FORMAT = new int[]{
            PROC_SPACE_TERM | PROC_OUT_STRING,
            PROC_SPACE_TERM | PROC_OUT_STRING,
            PROC_SPACE_TERM | PROC_OUT_LONG,
            PROC_SPACE_TERM | PROC_OUT_LONG,
            PROC_SPACE_TERM | PROC_OUT_LONG,
            PROC_SPACE_TERM | PROC_OUT_LONG,
            PROC_SPACE_TERM | PROC_OUT_LONG,
            PROC_SPACE_TERM | PROC_OUT_LONG,
            PROC_SPACE_TERM | PROC_OUT_LONG,
            PROC_SPACE_TERM | PROC_OUT_LONG,
            PROC_SPACE_TERM | PROC_OUT_LONG,
            PROC_SPACE_TERM | PROC_OUT_LONG,
            PROC_SPACE_TERM | PROC_OUT_LONG,
            PROC_SPACE_TERM | PROC_OUT_LONG,                  // 13: utime
            PROC_SPACE_TERM | PROC_OUT_LONG                   // 14: stime
    };

//    private static final int[] PROCESS_STATS_FORMAT = new int[]{
//            PROC_SPACE_TERM | PROC_OUT_STRING,
//            PROC_SPACE_TERM | PROC_OUT_STRING,
//            PROC_SPACE_TERM | PROC_OUT_LONG,
//            PROC_SPACE_TERM | PROC_OUT_LONG,
//            PROC_SPACE_TERM | PROC_OUT_LONG,
//            PROC_SPACE_TERM | PROC_OUT_LONG,
//            PROC_SPACE_TERM | PROC_OUT_LONG,
//            PROC_SPACE_TERM | PROC_OUT_LONG,
//            PROC_SPACE_TERM | PROC_OUT_LONG,
//            PROC_SPACE_TERM | PROC_OUT_LONG,
//            PROC_SPACE_TERM | PROC_OUT_LONG,
//            PROC_SPACE_TERM | PROC_OUT_LONG,
//            PROC_SPACE_TERM | PROC_OUT_LONG,
//            PROC_SPACE_TERM | PROC_OUT_LONG,                  // 13: utime
//            PROC_SPACE_TERM | PROC_OUT_LONG                   // 14: stime
//    };

    /**
     * Used to get total time from /proc/stat
     */
    private static final int[] PROCESS_TOTAL_STATS_FORMAT = new int[]{
            PROC_SPACE_TERM,
            PROC_SPACE_TERM | PROC_OUT_LONG, // ?? discarded
            PROC_SPACE_TERM | PROC_OUT_LONG, // user
            PROC_SPACE_TERM | PROC_OUT_LONG, // nice
            PROC_SPACE_TERM | PROC_OUT_LONG, // system
            PROC_SPACE_TERM | PROC_OUT_LONG, // idle
            PROC_SPACE_TERM | PROC_OUT_LONG, // iowait
            PROC_SPACE_TERM | PROC_OUT_LONG, // irq
            PROC_SPACE_TERM | PROC_OUT_LONG, // softirq
    };
    private static final int[] PROC_MEMINFO_FORMAT = new int[]{
            PROC_SPACE_TERM | PROC_COMBINE, PROC_SPACE_TERM | PROC_OUT_LONG, PROC_LINE_TERM,
            PROC_SPACE_TERM | PROC_COMBINE, PROC_SPACE_TERM | PROC_OUT_LONG, PROC_LINE_TERM,
            PROC_SPACE_TERM | PROC_COMBINE, PROC_SPACE_TERM | PROC_OUT_LONG, PROC_LINE_TERM,
            PROC_SPACE_TERM | PROC_COMBINE, PROC_SPACE_TERM | PROC_OUT_LONG, PROC_LINE_TERM,
    };
    //</editor-fold>

    private static final int STARTUP_PROCESS_NUMBER = 240;  //Estimated process number at startup
    private static final String COMMAND_TERMINATOR = "--TERM--";
    private static final Object suProcessPidUidSynch = new Object();
    private static final Object suProcessTimeSynch = new Object();
    private static SystemInfo instance = new SystemInfo();
    private static ConcurrentHashMap<Integer, Integer> mapPidUid;
    private static ConcurrentHashMap<Integer, long[]> mapPidUsrSysTime;
    private static Context context;
    private static float pixelConversionScale = 1.0F;
    private static java.lang.Process suProcessPidUid;
    private static DataOutputStream suProcessPidUidInput;
    private static BufferedReader suProcessPidUidOutput;
    private static java.lang.Process suProcessTime;
    private static DataOutputStream suProcessTimeInput;
    private static BufferedReader suProcessTimeOutput;


    SparseArray<UidCacheEntry> uidCache = new SparseArray<UidCacheEntry>();
    // TODO: 12/08/16 sostituire con implementazioni, TOGLIERE RIFLESSIONE?
    /* We are going to take advantage of the hidden API within Process.java that
     * makes use of JNI so that we can perform suProcessTimeOutput top task efficiently.
     */
    private Field fieldUid;
    private Method methodGetUidForPid;
    private Method methodGetPids;
    private Method methodReadProcFile;
    private Method methodGetProperty;
    private long[] readBuf;


    @SuppressWarnings("unchecked")
    /**
     * REFLECTION
     */
    private SystemInfo() {
        Log.i(TAG, "SystemInfo: CREATO");

        suProcessPidUid = null;
        suProcessPidUidInput = null;
        suProcessPidUidOutput = null;
        suProcessTime= null;
        suProcessTimeInput = null;
        suProcessTimeOutput = null;

        startSuProcesses();

        //<editor-fold desc="REFLECTION">
        try {
            fieldUid = ActivityManager.RunningAppProcessInfo.class.getField("uid");
        } catch (NoSuchFieldException e) {
            /* API level 3 doesn't have this field unfortunately. */
        }

        try {
            methodGetUidForPid = Process.class.getMethod("getUidForPid", int.class);
        } catch (NoSuchMethodException e) {
            Log.w(TAG, "Could not access getUidForPid method");
        }

        try {
            methodGetPids = Process.class.getMethod("getPids", String.class,
                    int[].class);
        } catch (NoSuchMethodException e) {
            Log.w(TAG, "Could not access getPids method");
        }

        try {
            methodReadProcFile = Process.class.getMethod("readProcFile", String.class,
                    int[].class, String[].class, long[].class, float[].class);
        } catch (NoSuchMethodException e) {
            Log.w(TAG, "Could not access readProcFile method");
        }

        try {
            Class classSystemProperties = Class.forName("android.os.SystemProperties");
            methodGetProperty = classSystemProperties.getMethod("get", String.class);
        } catch (NoSuchMethodException e) {
            Log.w(TAG, "Could not access SystemProperties.get");
        } catch (ClassNotFoundException e) {
            Log.w(TAG, "Could not find class android.os.SystemProperties");
        }
        //</editor-fold>

        //Instantiate the hashmaps with a hypothetical number of process
        mapPidUid = new ConcurrentHashMap<>(STARTUP_PROCESS_NUMBER);
        mapPidUsrSysTime = new ConcurrentHashMap<>(STARTUP_PROCESS_NUMBER);

        //<editor-fold desc="TIMER">
        //Schedule timer to update hashmap
        new Timer().scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                SystemInfo.updatePidUidMap();
            }
        }, 0, PowerEstimator.ITERATION_INTERVAL);

        new Timer().scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                SystemInfo.updatePidUsrSysTimeMap();
            }
        }, 0, PowerEstimator.ITERATION_INTERVAL);
        //</editor-fold>

        Log.i(TAG, "SystemInfo: Timer created");

        readBuf = new long[1];

    }

    public static SystemInfo getInstance() {
        return instance;
    }

    /**
     * Start SU process
     */
    public static void startSuProcesses() {
        try {
            if (isSuProcessPidUidAlive())
                return;
            suProcessPidUid = Runtime.getRuntime().exec("su");
            //suProcessPidUid.waitFor();
            suProcessPidUidInput = new DataOutputStream(suProcessPidUid.getOutputStream());
            suProcessPidUidOutput = new BufferedReader(new InputStreamReader(suProcessPidUid.getInputStream()));
            Log.i(TAG, "startSuProcesses: SU(pu) started");
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            if (isSuProcessTimeAlive())
                return;
            suProcessTime = Runtime.getRuntime().exec("su");
            //suProcessPidUid.waitFor();
            suProcessTimeInput = new DataOutputStream(
                    suProcessTime.getOutputStream());
            suProcessTimeOutput = new BufferedReader(
                    new InputStreamReader(suProcessTime.getInputStream()));
            Log.i(TAG, "startSuProcesses: SU(time) started");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Stop SU process
     */
    public static void stopSuProcess() {
        try {
            synchronized (suProcessPidUidSynch) {
                if (!isSuProcessPidUidAlive())
                    return;

                suProcessPidUidInput.writeBytes("exit\n");
                suProcessPidUidInput.flush();

                suProcessPidUid.waitFor();
                suProcessPidUidInput.close();
                suProcessPidUidOutput.close();

                suProcessPidUid = null;
                suProcessPidUidInput = null;
                suProcessPidUidOutput = null;
                Log.i(TAG, "stopSuProcess: SU stopped");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            synchronized (suProcessTimeSynch) {
                if (!isSuProcessTimeAlive())
                    return;

                suProcessTimeInput.writeBytes("exit\n");
                suProcessTimeInput.flush();

                suProcessTime.waitFor();
                suProcessTimeInput.close();
                suProcessTimeOutput.close();

                suProcessTime = null;
                suProcessTimeInput = null;
                suProcessTimeOutput = null;
                Log.i(TAG, "stopSuProcess: SU stopped");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Check if SU process is alive
     *
     * @return true if is alive
     */
    private static boolean isSuProcessPidUidAlive() {
        return (suProcessPidUid != null) && (suProcessPidUidInput != null) && (suProcessPidUidOutput != null);
    }

    /**
     * Check if SU process is alive
     *
     * @return true if is alive
     */
    private static boolean isSuProcessTimeAlive() {
        return (suProcessTime != null) && (suProcessTimeInput != null) && (suProcessTimeOutput != null);
    }


    // TODO: 27/08/16 spostare gli update in un servizio?
    /**
     * Update the mapPidUid hashmap
     */
    public static void updatePidUidMap() {
        //Log.d(TAG, "updatePidUidMap: ");
        try {
            synchronized (suProcessPidUidSynch) {
                mapPidUid.clear();

                if (!SystemInfo.isSuProcessPidUidAlive())
                    return;

                suProcessPidUidInput.writeBytes("/system/xbin/ps -o pid,user; echo " + COMMAND_TERMINATOR + "\n");
                suProcessPidUidInput.flush();

                // Skip first line
                String line = suProcessPidUidOutput.readLine();

                int pid, uid, i = 0;

                while (true) {
                    try {
                        line = suProcessPidUidOutput.readLine();
                        if (line == null || line.compareTo(COMMAND_TERMINATOR) == 0)
                            break;

                        line = line.trim();
                        String[] token = line.split(" ");

                        if (token.length != 2) {
                            Log.e(TAG, "updatePidUidMap: ->" + line);
                        }

                        pid = Integer.parseInt(token[0]);
                        uid = Integer.parseInt(token[1]);

//                        if(i>210)
//                            Log.i(TAG, "updatePidUidMap: " + pid + " - " + uid + " - " + i);

                        i++;

                        mapPidUid.put(pid, uid);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                //suProcessPidUid.waitFor();
                //outputStream.close();
                //bufferedReader.close();

                //Log.i(TAG, "updatePidUidMap: updated[" + i + "]");
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Update the mapPidUsrSysTime hashmap
     */
    public static void updatePidUsrSysTimeMap() {
        try {

            synchronized (suProcessTimeSynch) {
                mapPidUsrSysTime.clear();

                if (!SystemInfo.isSuProcessPidUidAlive())
                    return;

                suProcessTimeInput.writeBytes("for i in `ls /proc | /system/xbin/grep -E \"^[0-9]+\"`; " +
                        "do cat \"/proc/${i}/stat\" 2>/dev/null; " +
                        "done; echo " + COMMAND_TERMINATOR + "\n");

//                suProcessPidUidInput.writeBytes("proc-cat; echo " + COMMAND_TERMINATOR + "\n");
                suProcessTimeInput.flush();

                String line;

                int usr, sys, pid, i = 0;
                while (true) {
                    try {
                        line = suProcessTimeOutput.readLine();
                        if (line == null || line.compareTo(COMMAND_TERMINATOR) == 0)
                            break;

                        i++;

                        //Log.v(TAG, "updatePidUsrSysTimeMap: " + line);
                        String[] token = line.split(" ");

                        pid = Integer.parseInt(token[0]);
                        usr = Integer.parseInt(token[13]); //utime
                        sys = Integer.parseInt(token[14]); //stime

                        long[] val = new long[2]; //I assume that max(INDEX_USER_TIME, INDEX_SYS_TIME) = 1
                        val[INDEX_USER_TIME] = usr;
                        val[INDEX_SYS_TIME] = sys;

                        mapPidUsrSysTime.put(pid, val); //INDEX_USER_TIME e INDEX_SYS_TIME

                        //Log.v(TAG, "updatePidUsrSysTimeMap: " + pid + " - " + usr + " - " + sys);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                //Log.i(TAG, "updatePidUsrSysTimeMap: " + i);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Search the user that own a specific process
     *
     * @param pid Process id
     * @return uid owener of pid
     */
    public int getUidForPid(int pid) {
        try {
            if (mapPidUid.containsKey(pid))
                return mapPidUid.get(pid);
        } catch (Exception e){
            e.printStackTrace();
        }
        return -1;
    }

    /**
     * Get active time for the specified PID times should contain two elements
     *
     * @param pid   Process id
     * @param times times[INDEX_USER_TIME] will contains user time and times[INDEX_SYS_TIME] will
     *              contains sys time for this pid
     * @return true on success
     */
    public boolean getPidUsrSysTime(int pid, long[] times) {
        try {
            if (mapPidUsrSysTime.containsKey(pid)) {
                long[] val = mapPidUsrSysTime.get(pid);
                times[INDEX_USER_TIME] = val[INDEX_USER_TIME];
                times[INDEX_SYS_TIME] = val[INDEX_SYS_TIME];
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }


    public void setContext(Context context) {
        SystemInfo.context = context;
        pixelConversionScale = context.getResources().getDisplayMetrics().density;
    }

    public int getUidForProcessInfo(ActivityManager.RunningAppProcessInfo app) {
    /* Try to access the uid field first if it is avaialble. Otherwise just
     * convert the pid to a uid.
     */
        if (fieldUid != null) try {
            return (Integer) fieldUid.get(app);
        } catch (IllegalAccessException e) {
        }
        return getUidForPid(app.pid);
    }

    /**
     * lastPids can be null.  It is just used to avoid memory reallocation if
     * at all possible. Returns null on failure. If lastPids can hold the new
     * pid list the extra entries will be filled with -1 at the end.
     * @param lastPids
     * @return
     */
    public int[] getPids(int[] lastPids) {
        if (methodGetPids == null)
            return manualGetInts("/proc", lastPids);
        try {
            return (int[]) methodGetPids.invoke(null, "/proc", lastPids);
        } catch (IllegalAccessException e) {
            Log.w(TAG, "Failed to get process cpu usage");
        } catch (InvocationTargetException e) {
            Log.w(TAG, "Exception thrown while getting cpu usage");
        }
        return null;
    }


    /**
     * Gets a property on Android accessible through getprop
     * @param property Property requested
     * @return Property values
     */
    public String getProperty(String property) {
        if (methodGetProperty == null) return null;
        try {
            return (String) methodGetProperty.invoke(null, property);
        } catch (IllegalAccessException e) {
            Log.w(TAG, "Failed to get property");
        } catch (InvocationTargetException e) {
            Log.w(TAG, "Exception thrown while getting property");
        }
        return null;
    }

    /**
     * lastUids can be null.  It is just used to avoid memory reallocation if
     * at all possible. Returns null on failure. If lastUids can hold the new
     * uid list the extra entries will be filled with -1 at the end.
     * @param lastUids
     * @return
     */
    public int[] getUids(int[] lastUids) {
        if (methodGetPids == null) return manualGetInts("/proc/uid_stat", lastUids);
        try {
            return (int[]) methodGetPids.invoke(null, "/proc/uid_stat", lastUids);
        } catch (IllegalAccessException e) {
            Log.w(TAG, "Failed to get process cpu usage");
        } catch (InvocationTargetException e) {
            Log.w(TAG, "Exception thrown while getting cpu usage");
        }
        return null;
    }

    private int[] manualGetInts(String dir, int[] lastInts) {
        File[] files = new File(dir).listFiles();
        int sz = files == null ? 0 : files.length;
        if (lastInts == null || lastInts.length < sz) {
            lastInts = new int[sz];
        } else if (2 * sz < lastInts.length) {
            lastInts = new int[sz];
        }
        int pos = 0;
        for (int i = 0; i < sz; i++) {
            try {
                int v = Integer.parseInt(files[i].getName());
                lastInts[pos++] = v;
            } catch (NumberFormatException e) {
            }
        }
        while (pos < lastInts.length) lastInts[pos++] = -1;
        return lastInts;
    }


    /**
     * Get The time spent in User mode, System mode and Idle
     * @param times Times should contain 8 elements.  times[INDEX_USER_TIME] will be filled
     * with the total user time, times[INDEX_SYS_TIME] will be filled
     * with the total sys time, and times[INDEX_TOTAL_TIME] will have the total
     * time (including idle cycles).
     * @return true on success
     */
    public boolean getUsrSysTotalTime(long[] times) {
        // TODO: 23/08/16 leggere i valori per singola CPU o la somma?
        /* Secondo me è meglio la somma, altrimenti per ogni processo sarebbe necesario
         * Vedere su quale CPU è stato eseguito nel corso dell'iterazione
         */
        if (methodReadProcFile == null) return false;
        try {
            if ((Boolean) methodReadProcFile.invoke(
                    null, "/proc/stat",
                    PROCESS_TOTAL_STATS_FORMAT, null, times, null)) {
                long usr = times[1] + times[2];                 // user + nice
                long sys = times[3] + times[6] + times[7];      // system + irq + softirq
                long total = usr + sys + times[4] + times[5];   // (1) + (2) + idle + iowait
                times[INDEX_USER_TIME] = usr;
                times[INDEX_SYS_TIME] = sys;
                times[INDEX_TOTAL_TIME] = total;
                return true;
            }
        } catch (IllegalAccessException e) {
            Log.w(TAG, "Failed to get total cpu usage");
        } catch (InvocationTargetException e) {
            Log.w(TAG, "Exception thrown while getting total cpu usage");
        }
        return false;
    }

    /**
     * mem should contain 4 elements.  mem[INDEX_MEM_TOTAL] will contain total
     * memory available in kb, mem[INDEX_MEM_FREE] will give the amount of free
     * memory in kb, mem[INDEX_MEM_BUFFERS] will give the size of kernel buffers
     * in kb, and mem[INDEX_MEM_CACHED] will give the size of kernel caches in kb.
     * Returns true on success.
     * @param mem
     * @return
     */
    public boolean getMemInfo(long[] mem) {
        if (methodReadProcFile == null) return false;
        try {
            if ((Boolean) methodReadProcFile.invoke(
                    null, "/proc/meminfo",
                    PROC_MEMINFO_FORMAT, null, mem, null)) {
                return true;
            }
        } catch (IllegalAccessException e) {
            Log.w(TAG, "Failed to get mem info");
        } catch (InvocationTargetException e) {
            Log.w(TAG, "Exception thrown while getting mem info");
        }
        return false;
    }

    /* Returns -1 on failure. */
    public long readLongFromFile(String file) {
        if (methodReadProcFile == null)
            return -1;
        try {
            if ((Boolean) methodReadProcFile.invoke(
                    null, file, READ_LONG_FORMAT, null, readBuf, null)) {
                return readBuf[0];
            }
        } catch (IllegalAccessException e) {
            Log.w(TAG, "Failed to get pid cpu usage");
        } catch (InvocationTargetException e) {
            Log.w(TAG, "Exception thrown while getting pid cpu usage");
        }
        return -1L;
    }

    /**
     * Search package name from uid using (if possible) cached data
     *
     * @param uid uid we want to know the name
     * @param pm  package manager
     * @return
     */
    public synchronized String getAppId(int uid, PackageManager pm) {
        // Check if present in cache
        UidCacheEntry cacheEntry = uidCache.get(uid);
        if (cacheEntry == null) {
            // If not, add to cache
            cacheEntry = new UidCacheEntry();
            uidCache.put(uid, cacheEntry);
        }

        cacheEntry.clearIfExpired();

        // Search if information in cache is valid
        if (cacheEntry.getAppId() != null) {
            return cacheEntry.getAppId();
        }

        // If not, search manually
        String result = getAppIdNoCache(uid, pm);
        cacheEntry.setAppId(result);
        return result;
    }


    private String getAppIdNoCache(int uid, PackageManager pm) {
        if (uid < SystemInfo.AID_APP) {
            Log.e(TAG, "Only pass application uids to getAppId");
            return null;
        }
        int versionCode = -1;
        String[] packages = pm.getPackagesForUid(uid);
        if (packages != null) for (String packageName : packages) {
            try {
                PackageInfo info = pm.getPackageInfo(packageName, 0);
                versionCode = info.versionCode;
            } catch (PackageManager.NameNotFoundException e) {
            }
        }
        String name = pm.getNameForUid(uid);
        name = name == null ? "none" : name;
        return pm.getNameForUid(uid) + "@" + versionCode;
    }

    public synchronized String getUidName(int uid, PackageManager pm) {
        UidCacheEntry cacheEntry = uidCache.get(uid);
        if (cacheEntry == null) {
            cacheEntry = new UidCacheEntry();
            uidCache.put(uid, cacheEntry);
        }
        cacheEntry.clearIfExpired();
        if (cacheEntry.getName() != null) {
            return cacheEntry.getName();
        }
        String result = getUidNameNoCache(uid, pm);
        cacheEntry.setName(result);
        return result;
    }

    private String getUidNameNoCache(int uid, PackageManager pm) {
        switch (uid) {
            case AID_ROOT:
                return "Kernel";
            case AID_SYSTEM:
                return "System";
            case AID_RADIO:
                return "Radio Subsystem";
            case AID_BLUETOOTH:
                return "Bluetooth Subsystem";
            case AID_GRAPHICS:
                return "Graphics Devices";
            case AID_INPUT:
                return "Input Devices";
            case AID_AUDIO:
                return "Audio Devices";
            case AID_CAMERA:
                return "Camera Devices";
            case AID_LOG:
                return "Log Devices";
            case AID_COMPASS:
                return "Compass Device (e.g. akmd)";
            case AID_MOUNT:
                return "Mount";
            case AID_WIFI:
                return "Wifi Subsystem";
            case AID_ADB:
                return "Android Debug Bridge";
            case AID_INSTALL:
                return "Install";
            case AID_MEDIA:
                return "Media Server";
            case AID_DHCP:
                return "DHCP Client";
            case AID_SHELL:
                return "Debug Shell";
            case AID_CACHE:
                return "Cache Access";
            case AID_DIAG:
                return "Diagnostics";
        }
        if (uid < AID_APP) {
            return "sys_" + uid;
        }

        String[] packages = pm.getPackagesForUid(uid);
        if (packages != null) for (String packageName : packages) {
            try {
                PackageInfo info = pm.getPackageInfo(packageName, 0);
                CharSequence label = info.applicationInfo.loadLabel(pm);
                if (label != null) {
                    return label.toString();
                }
            } catch (PackageManager.NameNotFoundException e) {
            }
        }
        String uidName = pm.getNameForUid(uid);
        if (uidName != null) {
            return uidName;
        }
        return "app_" + uid;
    }

    public synchronized Drawable getUidIcon(int uid, PackageManager pm) {
        UidCacheEntry cacheEntry = uidCache.get(uid);
        if (cacheEntry == null) {
            cacheEntry = new UidCacheEntry();
            uidCache.put(uid, cacheEntry);
        }
        cacheEntry.clearIfExpired();
        if (cacheEntry.getIcon() != null) {
            return cacheEntry.getIcon();
        }
        Drawable result = getUidIconNoCache(uid, pm);
        cacheEntry.setIcon(result);
        return result;
    }

    public Drawable getUidIconNoCache(int uid, PackageManager pm) {
        String[] packages = pm.getPackagesForUid(uid);
        if (packages != null) for (int i = 0; i < packages.length; i++) {
            try {
                ApplicationInfo ai = pm.getApplicationInfo(packages[i], 0);
                if (ai.icon != 0) {
                    return ai.loadIcon(pm);
                }
            } catch (PackageManager.NameNotFoundException e) {
            }
        }
        return pm.getDefaultActivityIcon();
    }

    public synchronized void voidUidCache(int uid) {
        uidCache.remove(uid);
    }

    /**
     * Convert dp to equivalent pixel
     *
     * @param dp
     * @return screen-dependant size
     */
    public int dpToPixel(int dp) {
        return (int) (dp * pixelConversionScale + 0.5f);
    }

    private static class UidCacheEntry {
        private static long EXPIRATION_TIME = 1000 * 60 * 10; // 10 minutes

        private String appId;
        private String name;
        private Drawable icon;
        private long updateTime;

        public UidCacheEntry() {
            updateTime = -1;
        }

        public String getAppId() {
            return appId;
        }

        public void setAppId(String appId) {
            this.appId = appId;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
            if (updateTime == -1) {
                updateTime = SystemClock.elapsedRealtime();
            }
        }

        public Drawable getIcon() {
            return icon;
        }

        public void setIcon(Drawable icon) {
            this.icon = icon;
            if (updateTime == -1) {
                updateTime = SystemClock.elapsedRealtime();
            }
        }

        public void clearIfExpired() {
            if (updateTime != -1 &&
                    updateTime + EXPIRATION_TIME < SystemClock.elapsedRealtime()) {
                updateTime = -1;
                name = null;
                icon = null;
            }
        }
    }

    public class PidTime {

    }
}

