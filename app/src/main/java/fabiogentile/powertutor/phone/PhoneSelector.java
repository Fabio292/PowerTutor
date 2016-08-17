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
import android.os.Build;
import android.util.Log;

import java.util.List;

import fabiogentile.powertutor.components.CPU;
import fabiogentile.powertutor.components.PowerComponent;
import fabiogentile.powertutor.service.PowerData;

public class PhoneSelector {
    public static final int PHONE_UNKNOWN = 0;
    public static final int PHONE_DREAM = 1; /* G1 */
    public static final int PHONE_SAPPHIRE = 2; /* G2 */
    public static final int PHONE_PASSION = 3; /* Nexus One */
    public static final int PHONE_HAMMERHEAD = 4; /* Nexus 5 */
    public static final int PHONE_ROYSS = 5; /* Samsung GT-S6310N */

    /* A hard-coded list of phones that have OLED screens. */
    public static final String[] OLED_PHONES = {
            "bravo",
            "passion",
            "GT-I9000",
            "inc",
            "legend",
            "GT-I7500",
            "SPH-M900",
            "SGH-I897",
            "SGH-T959",
            "desirec",
    };

    private static final String TAG = "PhoneSelector";


    /* This class is not supposed to be instantiated.  Just use the static
     * members.
     */
    private PhoneSelector() {
    }

    public static boolean phoneSupported() {
        return getPhoneType() != PHONE_UNKNOWN;
    }

    public static boolean hasOled() {
        for (String OLED_PHONE : OLED_PHONES) {
            if (Build.DEVICE.equals(OLED_PHONE)) {
                return true;
            }
        }
        return false;
    }

    public static int getPhoneType() {
        //Log.i(TAG, "getPhoneType: " + Build.DEVICE);

        if (Build.DEVICE.startsWith("dream")) return PHONE_DREAM;
        if (Build.DEVICE.startsWith("sapphire")) return PHONE_SAPPHIRE;
        if (Build.DEVICE.startsWith("passion")) return PHONE_PASSION;
        if (Build.DEVICE.startsWith("hammerhead")) return PHONE_HAMMERHEAD;
        if (Build.DEVICE.startsWith("royssnfc")) return PHONE_ROYSS;


        return PHONE_UNKNOWN;
    }

    public static PhoneConstants getConstants(Context context) {
        switch (getPhoneType()) {
            case PHONE_DREAM:
                return new DreamConstants(context);
            case PHONE_SAPPHIRE:
                return new SapphireConstants(context);
            case PHONE_PASSION:
                return new PassionConstants(context);
            case PHONE_HAMMERHEAD:
                return new HammerheadConstants(context);
            case PHONE_ROYSS:
                return new RoyssConstants(context);
            default:
                boolean oled = hasOled();
                Log.w(TAG, "Phone type not recognized (" + Build.DEVICE + "), using " +
                        (oled ? "Passion" : "Dream") + " constants");
                return oled ? new PassionConstants(context) :
                        new DreamConstants(context);
        }
    }

    public static PhonePowerCalculator getCalculator(Context context) {
        switch (getPhoneType()) {
            case PHONE_DREAM:
                return new DreamPowerCalculator(context);
            case PHONE_SAPPHIRE:
                return new SapphirePowerCalculator(context);
            case PHONE_PASSION:
                return new PassionPowerCalculator(context);
            case PHONE_HAMMERHEAD:
                return new HammerheadPowerCalculator(context);
            case PHONE_ROYSS:
                return new RoyssPowerCalculator(context);
            default:
                boolean oled = hasOled();
                Log.w(TAG, "Phone type not recognized (" + Build.DEVICE + "), using " +
                        (oled ? "Passion" : "Dream") + " calculator");
                return oled ? new PassionPowerCalculator(context) :
                        new DreamPowerCalculator(context);
        }
    }

    public static void generateComponents(Context context, List<PowerComponent> components, List<PowerFunction> functions) {
        final PhoneConstants constants = getConstants(context);
        final PhonePowerCalculator calculator = getCalculator(context);

        //TODO: What about bluetooth?
        //TODO: LED light on the Nexus

//        /* Add display component. */
//        if (hasOled()) {
//            components.add(new OLED(context, constants));
//            functions.add(new PowerFunction() {
//                public double calculate(PowerData data) {
//                    return calculator.getOledPower((OLED.OledData) data);
//                }
//            });
//        } else {
//            components.add(new LCD(context, constants));
//            functions.add(new PowerFunction() {
//                public double calculate(PowerData data) {
//                    return calculator.getLcdPower((LCD.LcdData) data);
//                }
//            });
//        }

        /* Add CPU component. */
        components.add(new CPU(constants));
        functions.add(new PowerFunction() {
            public double calculate(PowerData data) {
                return calculator.getCpuPower((CPU.CpuData) data);
            }
        });


//        /* Add Wifi component. */
//        String wifiInterface =
//                SystemInfo.getInstance().getProperty("wifi.interface");
//        if (wifiInterface != null && wifiInterface.length() != 0) {
//            components.add(new Wifi(context, constants));
//            functions.add(new PowerFunction() {
//                public double calculate(PowerData data) {
//                    return calculator.getWifiPower((Wifi.WifiData) data);
//                }
//            });
//        }

//        /* Add 3G component. */
//        if (constants.threegInterface().length() != 0) {
//            components.add(new Threeg(context, constants));
//            functions.add(new PowerFunction() {
//                public double calculate(PowerData data) {
//                    return calculator.getThreeGPower((ThreegData) data);
//                }
//            });
//        }

//        /* Add GPS component. */
//        components.add(new GPS(context, constants));
//        functions.add(new PowerFunction() {
//            public double calculate(PowerData data) {
//                return calculator.getGpsPower((GpsData) data);
//            }
//        });

//        /* Add Audio component. */
//        components.add(new Audio(context));
//        functions.add(new PowerFunction() {
//            public double calculate(PowerData data) {
//                return calculator.getAudioPower((AudioData) data);
//            }
//        });
//
//        /* Add Sensors component if avaialble. */
//        if (NotificationService.available()) {
//            components.add(new Sensors(context));
//            functions.add(new PowerFunction() {
//                public double calculate(PowerData data) {
//                    return calculator.getSensorPower((SensorData) data);
//                }
//            });
//        }
    }
}
