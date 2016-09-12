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

import android.util.Log;
import android.util.SparseArray;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;

import fabiogentile.powertutor.phone.PhoneConstants;
import fabiogentile.powertutor.service.IterationData;
import fabiogentile.powertutor.service.PowerData;
import fabiogentile.powertutor.util.Recycler;
import fabiogentile.powertutor.util.SystemInfo;

public class CPU extends PowerComponent {
    private static final String TAG = "CPU";
    private static final String CPU_FREQ_FILE = "/proc/cpuinfo";
    private static final String STAT_FILE = "/proc/stat";
    private CpuStateKeeper cpuStateAll;
    private SparseArray<CpuStateKeeper> pidStates;
    private SparseArray<CpuStateKeeper> uidLinks;
    private int[] pids;
    private long[] statsBuf;
    private PhoneConstants constants;

    public CPU(PhoneConstants constants) {
        this.constants = constants;
        cpuStateAll = new CpuStateKeeper(SystemInfo.AID_ALL);
        pidStates = new SparseArray<CpuStateKeeper>();
        uidLinks = new SparseArray<CpuStateKeeper>();
        statsBuf = new long[8];
    }

    @Override
    public IterationData calculateIteration(long iteration) {
        IterationData result = IterationData.obtain();
        SystemInfo sysInfo = SystemInfo.getInstance();

        // Get current cpu freq
        double freq = readCpuFreq(sysInfo);
        if (freq < 0) {
            Log.w(TAG, "Failed to read cpu frequency");
            return result;
        }

        if (!sysInfo.getUsrSysTotalTime(statsBuf)) {
            Log.w(TAG, "Failed to read cpu times");
            return result;
        }

        long usrTime = statsBuf[SystemInfo.INDEX_USER_TIME];
        long sysTime = statsBuf[SystemInfo.INDEX_SYS_TIME];
        long totalTime = statsBuf[SystemInfo.INDEX_TOTAL_TIME];

        boolean init = cpuStateAll.isInitialized();
        cpuStateAll.updateState(usrTime, sysTime, totalTime, iteration);

        double userPercAll = 0.0;
        double sysPercAll = 0.0;

        if (init) {
            CpuData data = CpuData.obtain();
            // TODO: 24/08/16 0-100 -> 0-1, eventualmente correggere se cambiano le costanti
            userPercAll = cpuStateAll.getUsrPerc() / 100.0;
            sysPercAll = cpuStateAll.getSysPerc() / 100.0;
            data.init(sysPercAll, userPercAll, freq);
            result.setPowerData(data);
        } else {
            Log.e(TAG, "calculateIteration: ???");
        }

        uidLinks.clear();
        pids = sysInfo.getPids(pids);
        int pidInd = 0;
        //double powerPid = 0.0, powerUid = 0.0;
        if (pids != null) {

            //int err=0, ok=0;
            // Iterate through all pid
            for (int pid : pids) {
                if (pid < 0)
                    break;

                CpuStateKeeper pidState;
                // if we've seen this Pid before, get current state for this pid
                if (pidInd < pidStates.size() && pidStates.keyAt(pidInd) == pid) {
                    pidState = pidStates.valueAt(pidInd);
                } else {
                    // else create new StateKeeper and put into pidStates
                    int uid = sysInfo.getUidForPid(pid);
                    if (uid >= 0) {
                        // New process
                        pidState = new CpuStateKeeper(uid);
                        pidStates.put(pid, pidState);
                    } else {
                        // Assume that this process no longer exists.
                        continue;
                    }
                }
                pidInd++;

                if (!pidState.isStale(iteration)) {
                    /* Nothing much is going on with this pid recently.  We'll just
                     * assume that it's not using any of the cpu for this iteration.
                     */
                    //Log.i(TAG, "calculateIteration: Pid " + pid + " is stale (?)");
                    pidState.updateIteration(iteration, totalTime);
                } else if (sysInfo.getPidUsrSysTime(pid, statsBuf)) {
                    usrTime = statsBuf[SystemInfo.INDEX_USER_TIME];
                    sysTime = statsBuf[SystemInfo.INDEX_SYS_TIME];

                    init = pidState.isInitialized();
                    pidState.updateState(usrTime, sysTime, totalTime, iteration);

//                    if ((pidState.deltaUsr + pidState.deltaSys) > 50)
//                        Log.i(TAG, "calculateIteration: pid=" + pid + " U=" + pidState.deltaUsr + " S=" + pidState.deltaSys);
                    //ok++;

                    if (!init) {
                        continue;
                    }
//
//                    CpuData tmp = new CpuData();
//
//                    // Value are in SYS_TICK
//                    tmp.init(pidState.deltaSys / 100.0, pidState.deltaUsr / 100.0, freq);
//                    double power = HammerheadPowerCalculator.getTESTPower(tmp);
//                    powerPid += power;
//                    if (power > 0.0)
//                        Log.i(TAG, "calculateIteration: pid=" + pid + " -> " +
//                                String.format(Locale.getDefault(), "%1$.2f mW", power));

                } /*else {
                    //err++;
                    Log.e(TAG, "calculateIteration: impossible to get time data for pid " + pid);
                }*/


                // Merge different PID informations for the same UID
                CpuStateKeeper linkState = uidLinks.get(pidState.getUid());
                if (linkState == null) {
                    uidLinks.put(pidState.getUid(), pidState);
                } else {
                    linkState.absorb(pidState);
                }
            }
            //Log.i(TAG, "calculateIteration: OK="+ ok + " ERR=" + err);
        }


        // Remove processes that are no longer active.
        int deleted = 0;
        for (int i = 0; i < pidStates.size(); i++) {
            if (!pidStates.valueAt(i).isAlive(iteration)) {
                pidStates.remove(pidStates.keyAt(i--));
                deleted++;
            }
        }
        if (deleted > 0)
            Log.i(TAG, "calculateIteration: deleted=" + deleted);

        int compTime = 0;
        // Collect the summed uid information.
        for (int i = 0; i < uidLinks.size(); i++) {
            int uid = uidLinks.keyAt(i);
            CpuStateKeeper linkState = uidLinks.valueAt(i);
            CpuData uidData = CpuData.obtain();

            compTime += linkState.deltaUsr + linkState.deltaSys;

//            Log.i(TAG, "calculateIteration: uid=" + uid + " utime=" + linkState.deltaUsr + "(" + linkState.lastUsr
//                + ") stime=" + linkState.deltaSys + "(" + linkState.lastSys + ")");


            /* Il valore che ricavo dal file /proc/[pid]/stat(14,15) è espresso in JIFFIES
             * Per ottenere un valore in secondi bisogna dividere la somma dei due valori per la costante
             * USER_HZ (oppure HZ oppure CLOCK_PER_SEC) che corrisponde alla frequenza del kernel
             * Il valore di default è 100, ma può succede che sia diverso in kernel custom
             *
             * ATTENZIONE!!! Nel caso che il consumo dipenda dalla percentuale di carico piuttosto
             * piuttosto che dal tempo di utilizzo bisogna modificare la riga sotto
             * andando a riempire con i valori in percentuale
             * TODO serve davvero convertire da jiffies a ms? se tutti i valori di timing sono in
             * jffies credo di no
             */
            // TODO: 24/08/16 0-100 -> 0-1, eventualmente correggere se cambiano le costanti
            double userPerc = linkState.getUsrPerc() / 100.0;
            double sysPerc = linkState.getSysPerc() / 100.0;
            double sum = userPerc + sysPerc;

//            if(sum > 0.0)
//                Log.i(TAG, "calculateIteration: uid=" + uid + " " + sum + "%");
//            uidData.init(sysPerc, userPerc, freq);

            //uidData.init(sysPerc , userPerc , freq);
            uidData.init(userPerc, sysPerc, freq);

//            double sum = userPerc+sysPerc;
//            if(sum > 0.0)
//                Log.i(TAG, "calculateIteration: UID=" + uid + " " +
//                        String.format(Locale.getDefault(), "%1$.1f", sum) + "%");
//            double power = HammerheadPowerCalculator.getTESTPower(uidData);
//            if(power > 0.0)
//                Log.i(TAG, "calculateIteration: UID=" + uid + " " + power);

            // Non sembra abbia un utilizzo reale sta funzione
            //predictAppUidState(uidData, linkState.getUsrPerc(), linkState.getSysPerc(), freq);

            result.addUidPowerData(uid, uidData);
        }

        // compTime / #activeCores = CPU%

        //String allTime = String.format(Locale.getDefault(), "%1$.2f", userPercAll + sysPercAll);
        //Log.i(TAG, "calculateIteration: computation time: " + (float) (compTime / 100.0) + " (" + allTime + ")");


        return result;
    }


    /**
     * This is the function that is responsible for predicting the cpu frequency
     * state of the individual uid as though it were the only thing running.  It
     * simply is finding the lowest frequency that keeps the cpu usage under
     * 70% assuming there is a linear relationship to the cpu utilization at
     * different frequencies.
     */
    private void predictAppUidState(CpuData uidData, double usrPerc, double sysPerc, double freq) {
        double[] freqs = constants.cpuFreqs();
        if (usrPerc + sysPerc < 1e-6) {
            /* Don't waste time with the binary search if there is no utilization
             * which will be the case a lot.
             */
            uidData.init(sysPerc, usrPerc, freqs[0]);
            return;
        }
        int lo = 0;
        int hi = freqs.length - 1;
        double perc = sysPerc + usrPerc;
        while (lo < hi) {
            int mid = (lo + hi) / 2;
            double nperc = perc * freq / freqs[mid];
            if (nperc < 70) {
                hi = mid;
            } else {
                lo = mid + 1;
            }
        }
        uidData.init(sysPerc * freq / freqs[lo], usrPerc * freq / freqs[lo],
                freqs[lo]);
    }

    @Override
    public boolean hasUidInformation() {
        return true;
    }

    @Override
    public String getComponentName() {
        return "CPU";
    }

    /**
     * Returns the frequency of the processor in Mhz.  If the frequency cannot
     * be determined returns a negative value instead.
     * @param sysInfo
     * @return current frequency in mhz
     */
    private double readCpuFreq(SystemInfo sysInfo) {
        /* Try to read from the /sys/devices file first.  If that doesn't work
         * try manually inspecting the /proc/cpuinfo file.
         */
        long cpuFreqKhz = sysInfo.readLongFromFile(
                "/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq"); // TODO: 16/08/16 spostare nelle costanti?
        if (cpuFreqKhz != -1) {
            return cpuFreqKhz / 1000.0;
        }

        FileReader fstream;
        try {
            fstream = new FileReader(CPU_FREQ_FILE);
        } catch (FileNotFoundException e) {
            Log.w(TAG, "Could not read cpu frequency file");
            return -1;
        }
        BufferedReader in = new BufferedReader(fstream, 500);
        String line;
        try {
            while ((line = in.readLine()) != null) {
                if (line.startsWith("BogoMIPS")) {
                    return Double.parseDouble(line.trim().split("[ :]+")[1]);
                }
            }
        } catch (IOException e) {
        /* Failed to read from the cpu freq file. */
        } catch (NumberFormatException e) {
        /* Frequency not formatted properly as a double. */
        }
        Log.w(TAG, "Failed to read cpu frequency");
        return -1;
    }

    public static class CpuData extends PowerData {
        private static Recycler<CpuData> recycler = new Recycler<CpuData>();
        public double sysPerc;
        public double usrPerc;
        public double freq;
        public boolean isUidAll = false;

        private CpuData() {
        }

        public static CpuData obtain() {
            CpuData result = recycler.obtain();
            if (result != null) return result;
            return new CpuData();
        }

        public void setUidAll(boolean val) {
            this.isUidAll = val;
        }

        @Override
        public void recycle() {
            recycler.recycle(this);
        }

        public void init(double sysPerc, double usrPerc, double freq) {
            this.sysPerc = sysPerc;
            this.usrPerc = usrPerc;
            this.freq = freq;
        }

        public void writeLogDataInfo(OutputStreamWriter out) throws IOException {
            StringBuilder res = new StringBuilder();
            res.append("CPU-sys ").append(Math.round(sysPerc * 100))
                    .append("\nCPU-usr ").append(Math.round(usrPerc * 100))
                    .append("\nCPU-freq ").append(freq)
                    .append("\n");
            out.write(res.toString());
        }
    }

    private static class CpuStateKeeper {
        private int uid;
        private long iteration;
        private long lastUpdateIteration;
        private long inactiveIterations;

        private long lastUsr;
        private long lastSys;
        private long lastTotal;

        /**
         * Delta value in SYS_TICK (usually 100Hz) from last iteration
         */
        private long deltaUsr;
        /**
         * Delta value in SYS_TICK (usually 100Hz) from last iteration
         */
        private long deltaSys;
        /**
         * Delta value in SYS_TICK (usually 100Hz) from last iteration
         */
        private long deltaTotal;

        private CpuStateKeeper(int uid) {
            this.uid = uid;
            lastUsr = lastSys = -1;
            lastUpdateIteration = iteration = -1;
            inactiveIterations = 0;
        }

        public boolean isInitialized() {
            return lastUsr != -1;
        }

        public void updateIteration(long iteration, long totalTime) {
            /* Process is still running but actually reading the cpu utilization has
             * been skipped this iteration to avoid wasting cpu cycles as this process
             * has not been very active recently. */
            deltaUsr = 0;
            deltaSys = 0;
            deltaTotal = totalTime - lastTotal;
            if (deltaTotal < 1) deltaTotal = 1;
            lastTotal = totalTime;
            this.iteration = iteration;
        }

        public void updateState(long usrTime, long sysTime, long totalTime,
                                long iteration) {
            deltaUsr = usrTime - lastUsr;
            deltaSys = sysTime - lastSys;
            deltaTotal = totalTime - lastTotal;
            if (deltaTotal < 1) deltaTotal = 1;
            lastUsr = usrTime;
            lastSys = sysTime;
            lastTotal = totalTime;
            lastUpdateIteration = this.iteration = iteration;

            if (getUsrPerc() + getSysPerc() < 0.1) {
                inactiveIterations++;
            } else {
                inactiveIterations = 0;
            }
        }

        public int getUid() {
            return uid;
        }

        public void absorb(CpuStateKeeper s) {
            deltaUsr += s.deltaUsr;
            deltaSys += s.deltaSys;
        }

        public double getUsrPerc() {
            return 100.0 * deltaUsr / Math.max(deltaUsr + deltaSys, deltaTotal);
        }

        public double getSysPerc() {
            return 100.0 * deltaSys / Math.max(deltaUsr + deltaSys, deltaTotal);
        }

        public boolean isAlive(long iteration) {
            return this.iteration == iteration;
        }

        // TODO: 18/08/16 Capire il significato della funzione
        public boolean isStale(long iteration) {
            // if(2^DELTA_ITERATION > inactiveIteration^2)
            return 1L << (iteration - lastUpdateIteration) > inactiveIterations * inactiveIterations;
        }
    }
}
