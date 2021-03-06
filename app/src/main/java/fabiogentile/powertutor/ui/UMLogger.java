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

package fabiogentile.powertutor.ui;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AppOpsManager;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.zip.InflaterInputStream;

import fabiogentile.powertutor.ICounterService;
import fabiogentile.powertutor.R;
import fabiogentile.powertutor.phone.PhoneSelector;
import fabiogentile.powertutor.service.UMLoggerService;
import fabiogentile.powertutor.util.SystemInfo;

/**
 * The main view activity for PowerTutor
 */
public class UMLogger extends Activity {
    //    public static final String CURRENT_VERSION = "1.2"; // Don't change this...
//    public static final String SERVER_IP = "spidermonkey.eecs.umich.edu";
//    public static final int SERVER_PORT = 5204;
    private static final String TAG = "UMLogger";
    private static final int MENU_PREFERENCES = 0;
    private static final int MENU_SAVE_LOG = 1;
    private static final int DIALOG_START_SENDING = 0;
    private static final int DIALOG_STOP_SENDING = 1;
    private static final int DIALOG_TOS = 2;
    private static final int DIALOG_RUNNING_ON_STARTUP = 3;
    private static final int DIALOG_NOT_RUNNING_ON_STARTUP = 4;
    private static final int DIALOG_UNKNOWN_PHONE = 5;
    private static final int PERMISSION_REQUEST_CODE = 1;
    private SharedPreferences prefs;
    private Intent serviceIntent;
    private ICounterService counterService;
    private CounterServiceConnection conn;
    private Button serviceStartButton;
    private Button appViewerButton;
    private Button sysViewerButton;
    private Button helpButton;
    private TextView scaleText;

    //<editor-fold desc="Click Listener">
    private Button.OnClickListener appViewerButtonListener =
            new Button.OnClickListener() {
                public void onClick(View v) {
                    Intent intent = new Intent(v.getContext(), PowerTop.class);
                    startActivityForResult(intent, 0);
                }
            };

    private Button.OnClickListener sysViewerButtonListener =
            new Button.OnClickListener() {
                public void onClick(View v) {
                    Intent intent = new Intent(v.getContext(), PowerTabs.class);
                    startActivityForResult(intent, 0);
                }
            };

    private Button.OnClickListener serviceStartButtonListener =
            new Button.OnClickListener() {
                public void onClick(View v) {
                    serviceStartButton.setEnabled(false);
                    if (counterService != null) {
                        stopService(serviceIntent);
                        SystemInfo.stopSuProcess();
                    } else {
                        if (conn == null) {
                            Toast.makeText(UMLogger.this, "Profiler failed to start",
                                    Toast.LENGTH_SHORT).show();
                        } else {
                            startService(serviceIntent);
                            SystemInfo.startSuProcesses();
                        }
                    }
                }
            };

    private Button.OnClickListener helpButtonListener =
            new Button.OnClickListener() {
                public void onClick(View v) {
                    Intent myIntent = new Intent(v.getContext(), Help.class);
                    startActivityForResult(myIntent, 0);
                }
            };
    //</editor-fold>

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate: ");
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        serviceIntent = new Intent(this, UMLoggerService.class);
        conn = new CounterServiceConnection();

        SystemInfo.getInstance().setContext(getApplicationContext());

        setContentView(R.layout.main);
        ArrayAdapter<?> adapterxaxis = ArrayAdapter.createFromResource(
                this, R.array.xaxis, android.R.layout.simple_spinner_item);
        adapterxaxis.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);

        //If Marshmallow ask permission explicitly
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            askPermission();

        serviceStartButton = (Button) findViewById(R.id.servicestartbutton);
        appViewerButton = (Button) findViewById(R.id.appviewerbutton);
        sysViewerButton = (Button) findViewById(R.id.sysviewerbutton);
        helpButton = (Button) findViewById(R.id.helpbutton);

        serviceStartButton.setOnClickListener(serviceStartButtonListener);
        sysViewerButton.setOnClickListener(sysViewerButtonListener);
        appViewerButton.setOnClickListener(appViewerButtonListener);
        helpButton.setOnClickListener(helpButtonListener);

        if (counterService != null) {
            serviceStartButton.setText("Stop Profiler");
            appViewerButton.setEnabled(true);
            sysViewerButton.setEnabled(true);
        } else {
            serviceStartButton.setText("Start Profiler");
            appViewerButton.setEnabled(false);
            sysViewerButton.setEnabled(false);
        }
    }

    //<editor-fold desc="PERMISSION">

    /**
     * Check if all permissions are granted or not
     */
    @TargetApi(Build.VERSION_CODES.M)
    private void askPermission() {
        ArrayList<String> permList = new ArrayList<>();
        Context context = getApplicationContext();

        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            permList.add(Manifest.permission.ACCESS_FINE_LOCATION);

        if (context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED)
            permList.add(Manifest.permission.READ_PHONE_STATE);

        if (context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
            permList.add(Manifest.permission.READ_EXTERNAL_STORAGE);

        if (context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
            permList.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);


        //Check if some permission are needed
        if (permList.size() > 0) {
            String[] permArray = new String[permList.size()];

            int i = 0;
            for (String perm : permList) {
                permArray[i++] = perm;
            }

            ActivityCompat.requestPermissions(this, permArray, PERMISSION_REQUEST_CODE);
        }

        if (!hasUsageStatsPermission(this)) {
            startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
        }
    }


    @TargetApi(Build.VERSION_CODES.KITKAT)
    boolean hasUsageStatsPermission(Context context) {
        AppOpsManager appOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        int mode = appOps.checkOpNoThrow("android:get_usage_stats",
                android.os.Process.myUid(), context.getPackageName());
        boolean granted = mode == AppOpsManager.MODE_ALLOWED;
        return granted;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        for (int i = 0; i < permissions.length; i++) {
            Log.i(TAG, "onRequestPermissionsResult: " + permissions[i] + "=" + grantResults[i]);
        }
    }
    //</editor-fold>

    @Override
    public void onResume() {
        super.onResume();
        getApplicationContext().bindService(serviceIntent, conn, 0);
        if (prefs.getBoolean("firstRun", true)) {
            if (PhoneSelector.getPhoneType() == PhoneSelector.PHONE_UNKNOWN) {
                showDialog(DIALOG_UNKNOWN_PHONE);
            } else {
                showDialog(DIALOG_TOS);
            }
        }
        Intent startingIntent = getIntent();
        if (startingIntent.getBooleanExtra("isFromIcon", false)) {
            Intent copyIntent = (Intent) getIntent().clone();
            copyIntent.putExtra("isFromIcon", false);
            setIntent(copyIntent);
            Intent intent = new Intent(this, PowerTabs.class);
            startActivity(intent);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        getApplicationContext().unbindService(conn);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, MENU_PREFERENCES, 0, "Options");
        menu.add(0, MENU_SAVE_LOG, 0, "Save log");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_PREFERENCES:
                startActivity(new Intent(this, EditPreferences.class));
                return true;
            case MENU_SAVE_LOG:
                new Thread() {
                    public void start() {
                        File writeFile = new File(
                                Environment.getExternalStorageDirectory(), "PowerTrace" +
                                System.currentTimeMillis() + ".log");

                        try {
                            InflaterInputStream logIn = new InflaterInputStream(openFileInput("PowerTrace.log"));
                            BufferedOutputStream logOut = new BufferedOutputStream(new FileOutputStream(writeFile));

                            byte[] buffer = new byte[20480];
                            for (int ln = logIn.read(buffer); ln != -1;
                                 ln = logIn.read(buffer)) {
                                logOut.write(buffer, 0, ln);
                            }
                            logIn.close();
                            logOut.close();
                            Toast.makeText(UMLogger.this, "Wrote log to " + writeFile.getAbsolutePath(),
                                    Toast.LENGTH_SHORT).show();
                            return;

                        } catch (EOFException e) {
                            Toast.makeText(UMLogger.this, "Wrote log to " +
                                    writeFile.getAbsolutePath(), Toast.LENGTH_SHORT).show();
                            return;
                        } catch (IOException e) {
                            Log.e(TAG, "failed to save log: " + e.getMessage());
                        }
                        Toast.makeText(UMLogger.this, "Failed to write log to sdcard",
                                Toast.LENGTH_SHORT).show();
                    }
                }.start();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * This function includes all the dialog constructor
     */
    @Override
    protected Dialog onCreateDialog(int id) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        switch (id) {
            case DIALOG_TOS:
                builder.setMessage(R.string.term)
                        .setCancelable(false)
                        .setPositiveButton("Agree", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                prefs.edit().putBoolean("firstRun", false)
                                        .putBoolean("runOnStartup", true)
                                        .putBoolean("sendPermission", true).apply();
                                dialog.dismiss();
                            }
                        })
                        .setNegativeButton("Do not agree",
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int id) {
                                        prefs.edit().putBoolean("firstRun", true).apply();
                                        finish();
                                    }
                                });
                return builder.create();
            case DIALOG_STOP_SENDING:
                builder.setMessage(R.string.stop_sending_text)
                        .setCancelable(true)
                        .setPositiveButton("Stop", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                prefs.edit().putBoolean("sendPermission", false).apply();
                                dialog.dismiss();
                            }
                        })
                        .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                dialog.cancel();
                            }
                        });
                return builder.create();
            case DIALOG_START_SENDING:
                builder.setMessage(R.string.start_sending_text)
                        .setCancelable(true)
                        .setPositiveButton("Start", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                prefs.edit().putBoolean("sendPermission", true).apply();
                                dialog.dismiss();
                            }
                        })
                        .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                dialog.cancel();
                            }
                        });
                return builder.create();
            case DIALOG_RUNNING_ON_STARTUP:
                builder.setMessage(R.string.running_on_startup)
                        .setCancelable(true)
                        .setNeutralButton("Ok", null);
                return builder.create();
            case DIALOG_NOT_RUNNING_ON_STARTUP:
                builder.setMessage(R.string.not_running_on_startup)
                        .setCancelable(true)
                        .setNeutralButton("Ok", null);
                return builder.create();
            case DIALOG_UNKNOWN_PHONE:
                builder.setMessage(R.string.unknown_phone)
                        .setCancelable(false)
                        .setNeutralButton("Ok", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                dialog.dismiss();
                                showDialog(DIALOG_TOS);
                            }
                        });
                return builder.create();

        }
        return null;
    }

    //React to activation of service
    private class CounterServiceConnection implements ServiceConnection {
        public void onServiceConnected(ComponentName className,
                                       IBinder boundService) {
            counterService = ICounterService.Stub.asInterface(boundService);
            serviceStartButton.setText("Stop Profiler");
            serviceStartButton.setEnabled(true);
            appViewerButton.setEnabled(true);
            sysViewerButton.setEnabled(true);
        }

        public void onServiceDisconnected(ComponentName className) {
            counterService = null;
            getApplicationContext().unbindService(conn);
            getApplicationContext().bindService(serviceIntent, conn, 0);

            Toast.makeText(UMLogger.this, "Profiler stopped",
                    Toast.LENGTH_SHORT).show();
            serviceStartButton.setText("Start Profiler");
            serviceStartButton.setEnabled(true);
            appViewerButton.setEnabled(false);
            sysViewerButton.setEnabled(false);
        }
    }
}
