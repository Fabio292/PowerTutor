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
import android.hardware.Sensor;
import android.hardware.SensorManager;

import java.util.ArrayList;
import java.util.HashMap;

import fabiogentile.powertutor.components.Sensors;

/*
 * Values must be in mW
 * If we want to use the values from power_profile.xml (that are expressed in mA)
 * it's necessary to multiply the value by the nominal tension (3.7V ?)
 */
public class HammerheadConstants implements PhoneConstants {
    protected static final String OPER_TMOBILE = "T - Mobile";
    protected static final String OPER_ATT = "AT&T";


    //<editor-fold desc="CPU">
    /**
     * Values expressed for 100% cpu load with all cores at the same freq
     */
    private static final double[][] cpuPowerRatiosMatrix = {
    //300 422.4 652.8 729.6 883.2 960 1036.8
            // 1190.4 1267.2 1497.6 1574.4 1728 1958.4 2265.6
    {210.8, 324.6, 417.6, 474, 518.4, 687.6, 721.5,
            773.9, 923.1, 1021.6, 1123.3, 1205.4, 1356.9, 1622.6}, //1 core
    {383.2, 481, 602.6, 671.3, 768.2, 963.3, 1017.1,
            1136.2, 1328.3, 1641.6, 1787.7, 1995, 2322, 2950.4}, // 2 core
    {513, 623.1, 786.7, 888.4, 1223.6, 1347.9, 1438.6,
            1627, 1862.2, 2244.1, 2436.8, 2968.2, 3462.6, 4676.9}, // 3 core
    {634.5, 772.2, 1084.6, 1209.2, 1476.4, 1675.9, 1800.3,
            2071.5, 2360.4, 3019.4, 3307.8, 3881.1, 4810.6, 6890.1} // 4 core
    };

    private static final int CORE_NUMBER = 4;

    //Freqs in MHz
    private static final double[] cpuFreqsArray = {300.0, 422.4,
            652.8, 729.6, 883.2, 960.0,
            1036.8, 1190.4, 1267.2, 1497.6,
            1574.4, 1728.0, 1958.4, 2265.6};
    /**
     * List of maps of power consumption for CPU
     */
    public static ArrayList<HashMap<Double, Double>> cpuPowerList;


    /**
     * Base power value that must be added according to the max freq. IFF there are at least
     * 2 active cores
     */
    private static final double[] cpuBaseCorrectionArray = {0, 23.87,
            66.5, 78.8, 100.6, 100.6,
            260.7, 260.8, 260.7, 384.6,
            390.2, 431.3, 431.3, 431.3};

    public static HashMap<Double, Double> cpuBaseCorrectionMap;
    //</editor-fold>

    private static final double[] arrayGpsStatePower = {0.0, 173.55, 429.55};

    //<editor-fold desc="WIFI">
    private static final double[] arrayWifiLinkRatios = {
            47.122645, 46.354821, 43.667437, 43.283525, 40.980053, 39.44422, 38.676581,
            34.069637, 29.462693, 20.248805, 11.034917, 6.427122
    };
    private static final double[] arrayWifiLinkSpeeds = {
            1, 2, 5.5, 6, 9, 11, 12, 18, 24, 36, 48, 54
    };
    //</editor-fold>


    /* TODO: Figure out if this is really appropriate or how we should convert
     * the sensor power ratings (in mA) to mW.  I'm not sure we'll try to model
     * these thing's power usage but for the developer it's definitely interesting
     * to get some (perhaps rough?) idea of how much power sensors are using.
     */
    protected double BATTERY_VOLTAGE = 3.7;
    private double[] sensorPowerArray;

    public HammerheadConstants(Context context) {
        SensorManager sensorManager = (SensorManager) context.getSystemService(
                Context.SENSOR_SERVICE);
        sensorPowerArray = new double[Sensors.MAX_SENSORS];

        //Collect info about sensor power consumption
        for (int i = 0; i < Sensors.MAX_SENSORS; i++) {
            Sensor sensor = sensorManager.getDefaultSensor(i);
            if (sensor != null) {
                sensorPowerArray[i] = sensor.getPower() * BATTERY_VOLTAGE;
            }
        }

        //Populate cpu power hashmap
        cpuPowerList = new ArrayList<HashMap<Double, Double>>(CORE_NUMBER);
        // Iterate through cores
        for (int i = 0; i < CORE_NUMBER; i++) {
            HashMap<Double, Double> map = new HashMap<>(cpuFreqsArray.length);

            // Iterate through frequencies
            int j = 0;
            for (double f: cpuFreqsArray) {
                map.put(f, cpuPowerRatiosMatrix[i][j]);
                j++;
            }
            cpuPowerList.add(i, map);
        }

        // Populate cpu base power correction map
        cpuBaseCorrectionMap = new HashMap<>(cpuBaseCorrectionArray.length);
        for (int i = 0; i < cpuBaseCorrectionArray.length; i++) {
            cpuBaseCorrectionMap.put(cpuFreqsArray[i], cpuBaseCorrectionArray[i]);
        }

    }

    public String modelName() {
        return "hammerhead";
    }

    public double maxPower() {
        return 2300;
    }

    @Override
    public String backlightFile() {
        return "/sys/class/leds/lcd-backlight/brightness";
    }

    //<editor-fold desc="LCD">
    public double lcdBrightness(){
        //screen.full / 255
        return 3.8607;
    }

    public double lcdBacklight() {
        //screen.on + GPU_base (Young Geun Kim et al. 2014)
        return 274.1 + 90.8;
    }
    //</editor-fold>

    //<editor-fold desc="OLED">
    public double oledBasePower() {
        throw new RuntimeException("oledBasePower() called on device with no " +
                "OLED display");
    }

    public double[] oledChannelPower() {
        throw new RuntimeException("oledChannelPower() called on device with no " +
                "OLED display");
    }

    public double oledModulation() {
        throw new RuntimeException("oledModulation() called on device with no " +
                "OLED display");
    }
    //</editor-fold>

    //<editor-fold desc="CPU">
    public ArrayList<HashMap<Double, Double>> cpuPowerRatios() {
        return cpuPowerList;
    }

    public double[] cpuFreqs() {
        return cpuFreqsArray;
    }

    public double cpuBase() {
        return 61.26;
    }

    public HashMap<Double, Double> cpuBaseCorrection(){
        return cpuBaseCorrectionMap;
    }

    public int cpuCoreNumber() {
        return CORE_NUMBER;
    }
    //</editor-fold>

    public double audioPower() {
        return 384.62;
    }

    //<editor-fold desc="GPS">
    public double[] gpsStatePower() {
        return arrayGpsStatePower;
    }

    public double gpsSleepTime() {
        return 6.0;
    }
    //</editor-fold>

    //<editor-fold desc="WIFI">
    public double wifiOn() {
        return 12.95;
    }

    public double wifiLowPower() {
        return 0;
    }

    public double wifiHighPower() {
        return 293;
    }

    //PACKET PER SECONDS
    public double wifiLowHighTransition() {
        return 4;
    }

    public double wifiHighLowTransition() {
        return 4;
    }

    public double[] wifiLinkRatios() {
        return arrayWifiLinkRatios;
    }

    public double[] wifiLinkSpeeds() {
        return arrayWifiLinkSpeeds;
    }
    //</editor-fold>

    //<editor-fold desc="3G">
    public String threegInterface() {
        return "rmnet0";
    }

    public double threegIdlePower(String oper) {
        if (OPER_TMOBILE.equals(oper)) {
            return 10;
        }
        return 10;
    }

    public double threegFachPower(String oper) {
        if (OPER_TMOBILE.equals(oper)) {
            return 401;
        }
        return 401;
    }

    public double threegDchPower(String oper) {
        if (OPER_TMOBILE.equals(oper)) {
            return 570;
        }
        return 570;
    }

    public int threegDchFachDelay(String oper) {
        if (OPER_TMOBILE.equals(oper)) {
            return 6;
        } else if (OPER_ATT.equals(oper)) {
            return 5;
        }
        return 4;
    }

    public int threegFachIdleDelay(String oper) {
        if (OPER_TMOBILE.equals(oper)) {
            return 4;
        } else if (OPER_ATT.equals(oper)) {
            return 12;
        }
        return 6;
    }

    public int threegUplinkQueue(String oper) {
        return 151;
    }

    public int threegDownlinkQueue(String oper) {
        return 119;
    }
    //</editor-fold>

    public double[] sensorPower() {
        return sensorPowerArray;
    }

    // TODO: 11/09/16
    public double getMaxPower(String componentName) {
        if ("LCD".equals(componentName)) {
            return lcdBacklight() + lcdBrightness() * 255;
        } else if ("CPU".equals(componentName)) {
            ArrayList<HashMap<Double, Double>> ratios = cpuPowerRatios();
            return ratios.get(ratios.size() - 1).get(cpuFreqsArray[cpuFreqsArray.length - 1]);
        } else if ("Audio".equals(componentName)) {
            return audioPower();
        } else if ("GPS".equals(componentName)) {
            double[] gpsPow = gpsStatePower();
            return gpsPow[gpsPow.length - 1];
        } else if ("Wifi".equals(componentName)) {
            // TODO: Get a better estimation going here.
            return wifiHighPower() + wifiOn() + 50;
        } else if ("3G".equals(componentName)) {
            return threegDchPower("");
        } else if ("Sensors".equals(componentName)) {
            double res = 0;
            for (double x : sensorPower()) res += x;
            return res;
        } else {
            return 900;
        }
    }
}
