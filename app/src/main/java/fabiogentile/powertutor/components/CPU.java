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

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.MessageFormat;
import java.util.Arrays;

import fabiogentile.powertutor.phone.PhoneConstants;
import fabiogentile.powertutor.service.IterationData;
import fabiogentile.powertutor.service.PowerData;
import fabiogentile.powertutor.util.Recycler;
import fabiogentile.powertutor.util.SystemInfo;

public class CPU extends PowerComponent {
    private static final String TAG = "CPU";
    private static final String CPU_FREQ_FILE_BACK = "/proc/cpuinfo";
    private static final String STAT_FILE = "/proc/stat";
    private static final String CPU_FREQ_FILE =
            "/sys/devices/system/cpu/cpu{0}/cpufreq/scaling_cur_freq";
    private CpuStateKeeper cpuStateAll;
    private SparseArray<CpuStateKeeper> pidStates;
    private SparseArray<CpuStateKeeper> uidLinks;
    private int[] pids;
    private long[] statsBuf;
    private PhoneConstants constants;

    public CPU(PhoneConstants constants) {
        this.constants = constants;
        cpuStateAll = new CpuStateKeeper(SystemInfo.AID_ALL);
        pidStates = new SparseArray<>();
        uidLinks = new SparseArray<>();
        statsBuf = new long[8];
    }

    @Override
    public IterationData calculateIteration(long iteration){

        IterationData result = IterationData.obtain();
        SystemInfo sysInfo = SystemInfo.getInstance();

        // Get current cpu freq
        double[] freqs = readCpuFreq(sysInfo);
        if (freqs[0] < 0) {
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
            userPercAll = cpuStateAll.getUsrPerc() / 100.0;
            sysPercAll = cpuStateAll.getSysPerc() / 100.0;
            data.init(sysPercAll, userPercAll, freqs);
            result.setPowerData(data);
        } else {
            Log.e(TAG, "calculateIteration: ???");
        }

        uidLinks.clear();
        pids = sysInfo.getPids(pids);
        int pidInd = 0;

        int pidTime = 0;
        if (pids != null) {

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
                    pidState.updateIteration(iteration, totalTime);
                } else if (sysInfo.getPidUsrSysTime(pid, statsBuf)) {
                    usrTime = statsBuf[SystemInfo.INDEX_USER_TIME];
                    sysTime = statsBuf[SystemInfo.INDEX_SYS_TIME];

                    pidState.updateState(usrTime, sysTime, totalTime, iteration);

                }
                else{
                    Log.w(TAG, "calculateIteration: error fetching cpu usage for " + pid);
                }


                // Merge different PID informations for the same UID
                CpuStateKeeper linkState = uidLinks.get(pidState.getUid());
                if (linkState == null) {
                    uidLinks.put(pidState.getUid(), pidState);
                } else {
                    linkState.absorb(pidState);

                pidTime += pidState.deltaUsr + pidState.deltaSys;
                }
            }
        }

//        Log.i(TAG, "calculateIteration: totTime: " + (cpuStateAll.deltaSys + cpuStateAll.deltaUsr) + " pidTime: " + (pidTime));

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


            /* Il valore che ricavo dal file /proc/[pid]/stat(14,15) è espresso in JIFFIES
             * Per ottenere un valore in secondi bisogna dividere la somma dei due valori per la costante
             * USER_HZ (oppure HZ oppure CLOCK_PER_SEC) che corrisponde alla frequenza del kernel
             * Il valore di default è 100, ma può succede che sia diverso in kernel custom
             *
             * ATTENZIONE!!! Nel caso che il consumo dipenda dalla percentuale di carico
             * piuttosto che dal tempo di utilizzo bisogna modificare la riga sotto
             * andando a riempire con i valori in percentuale
             * TODO serve davvero convertire da jiffies a ms? se tutti i valori di timing sono in
             * jffies credo di no
             */
            double userPerc = linkState.getUsrPerc() / 100.0;
            double sysPerc = linkState.getSysPerc() / 100.0;

            uidData.init(userPerc, sysPerc, freqs);

            result.addUidPowerData(uid, uidData);
        }

        // compTime / #activeCores = CPU%

        //String allTime = String.format(Locale.getDefault(), "%1$.2f", userPercAll + sysPercAll);
        //Log.i(TAG, "calculateIteration: computation time: " + (float) (compTime / 100.0) + " (" + allTime + ")");


        return result;
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
     * @return array of frequency of all the cores
     */
    private double[] readCpuFreq(SystemInfo sysInfo) {
        int core = constants.cpuCoreNumber();

        double[] ret = new double[core];
        Arrays.fill(ret, -1);

        double cpuFreqKhz = 0;
        for (int i = 0; i < core; i++) {
            String fname = MessageFormat.format(CPU_FREQ_FILE, i);
            cpuFreqKhz = sysInfo.readLongFromFile(fname);
            if(cpuFreqKhz != -1)
                cpuFreqKhz /= 1000.0;

            ret[i] = cpuFreqKhz;
        }

        return ret;
    }

    public static class CpuData extends PowerData {
        private static Recycler<CpuData> recycler = new Recycler<CpuData>();
        public double sysPerc;
        public double usrPerc;
        public double[] freq;
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

        public void init(double sysPerc, double usrPerc, double[] pFreq) {
            this.sysPerc = sysPerc;
            this.usrPerc = usrPerc;

            this.freq = new double[pFreq.length];
            System.arraycopy(pFreq, 0, this.freq, 0, pFreq.length);
        }

        public void writeLogDataInfo(OutputStreamWriter out) throws IOException {

            StringBuilder freqString = new StringBuilder();
            for (Double f: freq) {
                freqString.append(f).append("+");
            }

            StringBuilder res = new StringBuilder();
            res.append("CPU+sys+").append(Math.round(sysPerc * 100))
                    .append("\nCPU+usr+").append(Math.round(usrPerc * 100))
                    .append("\nCPU+freq+").append(freqString)
                    .append("\n");
            out.write(res.toString());
        }
    }

    private static class CpuStateKeeper {
        /**
         * Delta value in SYS_TICK (usually 100Hz) from last iteration
         */
        public long deltaUsr;
        /**
         * Delta value in SYS_TICK (usually 100Hz) from last iteration
         */
        public long deltaSys;
        /**
         * Delta value in SYS_TICK (usually 100Hz) from last iteration
         */
        public long deltaTotal;
        private int uid;
        private long iteration;
        private long lastUpdateIteration;
        private long inactiveIterations;
        private long lastUsr;
        private long lastSys;
        private long lastTotal;

        private CpuStateKeeper(int uid) {
            this.uid = uid;
            lastUsr = lastSys = 0;
            lastUpdateIteration = iteration = 0;
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

            if (getUsrPerc() + getSysPerc() < 0.005) {
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

        public boolean isStale(long iteration) {
            // if(2^DELTA_ITERATION > inactiveIteration^2)
            return 1L << (iteration - lastUpdateIteration) > inactiveIterations * inactiveIterations;
        }
    }
}
