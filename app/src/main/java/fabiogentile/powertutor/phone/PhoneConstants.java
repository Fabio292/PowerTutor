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

public interface PhoneConstants {
    /* Return the name of the model represented by these constants
     */
    String modelName();

    /* Gives the maximum power that this phone can drain from its battery.
     */
    double maxPower();

    /**
     * Gives the path of the brightness file
     * @return File path
     */
    String backlightFile();

    /* Gives the coefficient to multiply the LCD display's brightness by to
     * calculate power usage.
     */
    double lcdBrightness();

    /* Gives the power usage for the lcd display that is incurred by the display
     * just being turned on.
     */
    double lcdBacklight();

    /* Gives the base power usage for the OLED display for just being on.
     */
    double oledBasePower();

    /* Gives the power coefficient for rgb channels (in that order) for a signle
     * pixel.
     */
    double[] oledChannelPower();

    /* Gives the modulation coefficient for the per pixel power calculation.
     */
    double oledModulation();

    /* Gives the coefficients at different cpu frequencies for the amount of
     * power/cpu utilization the processor is using.
     */
    double[] cpuPowerRatios();

    /* Gives the frequency for each of the power ratios listed in
     * cpuPowerRatios().
     */
    double[] cpuFreqs();

    /* Gives the usage for the audio output being used.  The model doesn't
     * currently take into account volume.
     */
    double audioPower();

    /* Gives the power consumption for each of the GPS states.  These states are
     * {OFF, SLEEP, ON} in that order.  See GPS.java.
     */
    double[] gpsStatePower();

    /* Gives the time in seconds that the GPS sleeps for after the session
     * has ended.
     */
    double gpsSleepTime();

    /* Gives the power consumption of wifi in the low power state.
     */
    double wifiLowPower();

    /* Gives the base power consumption while the wifi is in high power mode.
     */
    double wifiHighPower();

    /* Gives the packet rate needed to transition from the low power state
     * to the high power state.
     */
    double wifiLowHighTransition();

    /* Gives the packet rate needed to transition from the high power state
     * to the low power state.
     */
    double wifiHighLowTransition();

    /* Gives the power/uplinkrate for different link speeds for wifi in high
     * power mode.
     */
    double[] wifiLinkRatios();

    /* Gives the link speed associated with each link power ratio.  Elements
     * should be in increasing order.  Should have the same number of elements
     * as wifiLinkRatios().
     */
    double[] wifiLinkSpeeds();

    /* Gives the name of the 3G interface for this phone.
     */
    String threegInterface();

    /* Gives the power consumed while the 3G interface is in the idle state.
     */
    double threegIdlePower(String oper);

    /* Gives the power consumed while the 3G interface is in the FACH state.
     */
    double threegFachPower(String oper);

    /* Gives the power consumed while the 3G interface is in the DCH state.
     */
    double threegDchPower(String oper);

    /* Gives the number of bytes in the uplink queue.
     */
    int threegUplinkQueue(String oper);

    /* Gives the number of bytes in the downlink queue.
     */
    int threegDownlinkQueue(String oper);

    /* Gives the time in seconds that the 3G interface stays idle in the DCH state
     * before transitioning to the FACH state.
     */
    int threegDchFachDelay(String oper);

    /* Gives the time in seconds that the 3G interface stays idle in the FACH
     * state before transitioning to the IDLE state.
     */
    int threegFachIdleDelay(String oper);

    /* Gives the power consumed by each of the sensors.  Should have the same size
     * as Sensors.MAX_SENSORS.
     */
    double[] sensorPower();

    /* Gives the maximum power in mW that the named component can generate.
     */
    double getMaxPower(String componentName);
}
