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

package fabiogentile.powertutor.phone;

import android.content.Context;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;

import fabiogentile.powertutor.components.Audio.AudioData;
import fabiogentile.powertutor.components.CPU.CpuData;
import fabiogentile.powertutor.components.GPS;
import fabiogentile.powertutor.components.GPS.GpsData;
import fabiogentile.powertutor.components.LCD.LcdData;
import fabiogentile.powertutor.components.OLED.OledData;
import fabiogentile.powertutor.components.Sensors;
import fabiogentile.powertutor.components.Sensors.SensorData;
import fabiogentile.powertutor.components.Threeg;
import fabiogentile.powertutor.components.Threeg.ThreegData;
import fabiogentile.powertutor.components.Wifi;
import fabiogentile.powertutor.components.Wifi.WifiData;

public class HammerheadPowerCalculator implements PhonePowerCalculator {

    private static final String TAG = "HammerheadPC";
    protected PhoneConstants coeffs;
    private ArrayList<HashMap<Double, Double>> powerRatios;

    /**
     * Store for the various frequency how many time they appear across all active cores
     */
    private HashMap<Double, Integer> freqCountMap = new HashMap<>();

    public HammerheadPowerCalculator(Context context) {
        this(new HammerheadConstants(context));
    }

    protected HammerheadPowerCalculator(PhoneConstants coeffs) {
        this.coeffs = coeffs;
        this.powerRatios = coeffs.cpuPowerRatios();
    }

    /* Returns the largest index y such that if x were inserted into A (which
     * should already be sorted) at y then A would remain sorted.
     */
    protected static int upperBound(double[] A, double x) {
        int lo = 0;
        int hi = A.length;
        while (lo < hi) {
            int mid = lo + (hi - lo) / 2;
            if (A[mid] <= x) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

    public double getLcdPower(LcdData data) {
        double ret = data.screenOn ?
                coeffs.lcdBrightness() * data.brightness + coeffs.lcdBacklight() : 0;

        //Log.i(TAG, "getLcdPower: " + ret);

        return ret;
    }

    public double getOledPower(OledData data) {
        throw new RuntimeException("getOledPower() should not be called for Hammerhead");
    }

    public double getCpuPower(CpuData data) {
        /**     CPU POWER MODEL
         *
         * If the frequencies are all the same look at the matrix powerRatios
         *
         * Otherwise simply sum up the value for each frequency and subtract the
         * Base power consumption cumulated
         *
         * If there are more than 2 active cores, it can be necessary to adjust the base power cons.
         *
         */

        this.freqCountMap.clear();
        double maxFreq = analyzeFrequencies(data.freq, this.freqCountMap);

        double fullPower = 0;
        double ret = 0;
        int activeCores = 0;
        int differentFreq = this.freqCountMap.keySet().size();

        for (Double f: this.freqCountMap.keySet()) {
            if(f == -1)
                continue;

            int coresPerFreq = this.freqCountMap.get(f);
            activeCores += coresPerFreq;

            if((coresPerFreq - 1) > powerRatios.size()){
                Log.e(TAG, "getCpuPower: Requested count for core_number = " + coresPerFreq);
                continue;
            }

            HashMap<Double, Double> map = powerRatios.get(coresPerFreq - 1);

            if(!map.containsKey(f)){
                Log.e(TAG, "getCpuPower: Requested power for freq = " + f);
                continue;
            }

            // STEP 1: sum contribrutes from different frequency
            fullPower += map.get(f);
        }
        // STEP 2: Subtract cumulated base power
        fullPower -= coeffs.cpuBase() * (differentFreq - 1);

        // STEP 3: Scale the resulting power according the cpu usage for the current UID
        ret = Math.max(0, fullPower * (data.usrPerc + data.sysPerc));

        double correction;
        // STEP 4: Add, if necessary, add corrective factor to base power to the ALL uid
        if(data.isUidAll &&  activeCores > 1){
            correction = coeffs.cpuBaseCorrection().get(maxFreq);
            ret = ret + correction;
            //Log.d(TAG, "getCpuPower: correction: " + correction);
        }

        return ret;
    }

    /**
     * analyze the frequencies array by counting how much core are using same frequencies
     * and find the max value used to correct base powe
     */
    private double analyzeFrequencies (double[] freq, HashMap<Double, Integer> res){
        double ret = freq[0];
        for (double f: freq) {
            if(res.containsKey(f)){
                Integer val = res.get(f);
                res.put(f, val+1);
            }
            else{
                res.put(f, 1);
            }
        }

        return ret;
    }

    public double getAudioPower(AudioData data) {
        return data.musicOn ? coeffs.audioPower() : 0;
    }

    public double getGpsPower(GpsData data) {
        double result = 0;
        double statePower[] = coeffs.gpsStatePower();
        for (int i = 0; i < GPS.POWER_STATES; i++) {
            result += data.stateTimes[i] * statePower[i];
        }
        return result;
    }

    public double getWifiPower(WifiData data) {
        double ret;
        if (!data.wifiOn) {
            return 0;
        } else if (data.powerState == Wifi.POWER_STATE_LOW) {
            // Scale energy consumption basing on the percentage of data transmitted
            // Divide by 5 since the time slot is 200ms
            ret = coeffs.wifiHighPower() / 5 * data.uploadPercent  ;
        } else if (data.powerState == Wifi.POWER_STATE_HIGH) {
//            double[] linkSpeeds = coeffs.wifiLinkSpeeds();
//            double[] linkRatios = coeffs.wifiLinkRatios();
//            double ratio;
//            if (linkSpeeds.length == 1) {
//                /* If there is only one set speed we have to use its ratio as we have
//                 * nothing else to go on.
//                 */
//                ratio = linkRatios[0];
//            } else {
//                /* Find the two nearest speed/ratio pairs and linearly interpolate
//                 * the ratio for this link speed.
//                 */
//                int ind = upperBound(linkSpeeds, data.linkSpeed);
//                if (ind == 0) ind++;
//                if (ind == linkSpeeds.length) ind--;
//                ratio = linkRatios[ind - 1] + (linkRatios[ind] - linkRatios[ind - 1]) /
//                        (linkSpeeds[ind] - linkSpeeds[ind - 1]) *
//                        (data.linkSpeed - linkSpeeds[ind - 1]);
//            }
            // TODO: 12/10/16 add informations on link rate ?
            ret = coeffs.wifiHighPower() * data.uploadPercent;

        }
        else {
            throw new RuntimeException("Unexpected power state");
        }
        //Add base power
        ret += coeffs.wifiOn() * (data.uploadPercent + data.downloadPercent) / 2;
        return ret;
    }

    public double getThreeGPower(ThreegData data) {
        if (!data.threegOn) {
            return 0;
        } else {
            switch (data.powerState) {
                case Threeg.POWER_STATE_IDLE:
                    return coeffs.threegIdlePower(data.oper);
                case Threeg.POWER_STATE_FACH:
                    return coeffs.threegFachPower(data.oper);
                case Threeg.POWER_STATE_DCH:
                    return coeffs.threegDchPower(data.oper);
            }
        }
        return 0;
    }

    public double getSensorPower(SensorData data) {
        double result = 0;
        double[] powerUse = coeffs.sensorPower();
        for (int i = 0; i < Sensors.MAX_SENSORS; i++) {
            result += data.onTime[i] * powerUse[i];
        }
        return result;
    }
}

