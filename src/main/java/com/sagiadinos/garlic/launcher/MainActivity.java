/*
 garlic-launcher: Android Launcher for the Digital Signage Software garlic-player

 Copyright (C) 2020 Nikolaos Sagiadinos <ns@smil-control.com>
 This file is part of the garlic-player source code

 This program is free software: you can redistribute it and/or  modify
 it under the terms of the GNU Affero General Public License, version 3,
 as published by the Free Software Foundation.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU Affero General Public License for more details.

 You should have received a copy of the GNU Affero General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sagiadinos.garlic.launcher;


import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.support.annotation.NonNull;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.sagiadinos.garlic.launcher.helper.Installer;
import com.sagiadinos.garlic.launcher.helper.NavigationBar;
import com.sagiadinos.garlic.launcher.helper.Network;
import com.sagiadinos.garlic.launcher.helper.PlayerDownload;
import com.sagiadinos.garlic.launcher.helper.SharedConfiguration;
import com.sagiadinos.garlic.launcher.helper.DeviceOwner;
import com.sagiadinos.garlic.launcher.helper.HomeLauncherManager;
import com.sagiadinos.garlic.launcher.helper.KioskManager;
import com.sagiadinos.garlic.launcher.helper.LockTaskManager;
import com.sagiadinos.garlic.launcher.helper.AppPermissions;
import com.sagiadinos.garlic.launcher.receiver.ReceiverManager;

import java.io.IOException;


public class MainActivity extends Activity
{
    private boolean        has_second_app_started = false;
    private boolean        has_player_started     = false;
    private boolean        is_countdown_running   = false;

    private Button         btToggleLock = null;
    private Button         btToggleLauncher = null;
    private Button         btToggleServiceMode = null;
    private Button         btStartPlayer = null;
    private Button         btAdminConfiguration = null;
    private Button         btConfigureWiFi      = null;
    private TextView       tvInformation   = null;

    private CountDownTimer      PlayerCountDown        = null;
    private DeviceOwner         MyDeviceOwner          = null;
    private SharedConfiguration MySharedConfiguration = null;
    private KioskManager        MyKiosk               = null;

    @Override
    public void onRequestPermissionsResult(int request_code, @NonNull String[] permissions, @NonNull int[] grant_results)
    {
        AppPermissions.onRequestPermissionsResult(this, request_code, permissions, grant_results);
    }

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
         super.onCreate(savedInstanceState);

         setContentView(R.layout.main);
         tvInformation = findViewById(R.id.textViewInformation);
         initDebugButtons();
         MyDeviceOwner = new DeviceOwner(this);
         AppPermissions.verifyStandardPermissions(this);
         MySharedConfiguration = new SharedConfiguration(this);
         MyKiosk               = new KioskManager(MyDeviceOwner,
                                                new HomeLauncherManager(MyDeviceOwner, this),
                                                new LockTaskManager(this),
                                                MySharedConfiguration,
                                                this
        );

        MyDeviceOwner.deactivateRestrictions();
        if (!AppPermissions.hasStandardPermissions(this))
        {
            displayInformationText("Launcher needs read/write permissions for storage");
            return;
        }
        if (MyDeviceOwner.isDeviceOwner())
        {
            hideInformationText();
            ReceiverManager.registerAllReceiver(this);
            initButtonViews();
            startService(new Intent(this, WatchDogService.class)); // this is ok no nesting or leaks

            checkForInstalledPlayer();
            startGarlicPlayerDelayed();
        }
        else
        {
            displayInformationText(getString(R.string.no_device_owner));
        }
    }

    @Override
    protected void onDestroy()
    {
        if (MyDeviceOwner.isDeviceOwner())
        {
            ReceiverManager.unregisterAllReceiver(this);
        }
        super.onDestroy();
    }

    private void checkForInstalledPlayer()
    {
        if (PlayerDownload.isGarlicPlayerInstalled(MainActivity.this))
        {
            return;
        }
        if (Network.isConnected(MainActivity.this))
        {
            PlayerDownload MyPlayerDownload = new PlayerDownload(MainActivity.this);
            if (MyPlayerDownload.wasGarlicPlayerDownloaded())
            {
                try
                {
//                MyPlayerDownload.installDownloadedApp();
                    displayInformationText(getString(R.string.install_player_in_progress));
                    Installer MyInstaller = new Installer(MainActivity.this);
                    MyInstaller.installPackage(MyPlayerDownload.getApkPath());
                }
                catch (IOException e)
                {
                    displayInformationText(e.getMessage());
                }
            }
            else
            {
                MyPlayerDownload.startDownload();
                displayInformationText(getString(R.string.download_player_in_progress));
            }
        }
        else
        {
            displayInformationText(getString(R.string.no_garlic_info));
        }
    }

    private void initButtonViews()
    {
        btToggleServiceMode  = findViewById(R.id.btToggleServiceMode);
        btStartPlayer        = findViewById(R.id.btStartPlayer);
        btAdminConfiguration = findViewById(R.id.btAdminConfiguration);
        btConfigureWiFi      = findViewById(R.id.btConfigureWiFi);
        Button btContentUri  = findViewById(R.id.btSetContentURI);

        if (PlayerDownload.isGarlicPlayerInstalled(MainActivity.this))
        {
            btContentUri.setVisibility(View.VISIBLE);
            btStartPlayer.setVisibility(View.VISIBLE);
            hideInformationText();
        }
        else
        {
            btContentUri.setVisibility(View.INVISIBLE);
            btStartPlayer.setVisibility(View.INVISIBLE);
        }

        if (MySharedConfiguration.hasActiveServicePassword())
        {
            btToggleServiceMode.setVisibility(View.VISIBLE);
        }
        else
        {
            btToggleServiceMode.setVisibility(View.GONE);
        }

        if (MyKiosk.isStrictKioskModeActive())
        {
            btStartPlayer.setEnabled(false);
            btToggleServiceMode.setText(R.string.enter_service_mode);
            btAdminConfiguration.setVisibility(View.GONE);
            btConfigureWiFi.setVisibility(View.GONE);
            btContentUri.setVisibility(View.GONE);
        }
        else
        {
            btAdminConfiguration.setVisibility(View.VISIBLE);
            btStartPlayer.setEnabled(true);
            btToggleServiceMode.setText(R.string.exit_service_mode);
            btConfigureWiFi.setVisibility(View.VISIBLE);
            btContentUri.setVisibility(View.VISIBLE);
        }

        if (MyKiosk.startKioskMode() && btToggleLock != null) // Pin this app and set it as Launcher
        {
            btToggleLock.setText(R.string.unpin_app);
            btToggleLauncher.setText(R.string.restore_old_launcher);
        }
        NavigationBar.show(this, MySharedConfiguration);
    }



    public void initDebugButtons()
    {
        if (BuildConfig.DEBUG)
        {
            btToggleLock        = findViewById(R.id.btToggleLockTask);
            btToggleLauncher    = findViewById(R.id.btToggleLauncher);

            btToggleLock.setVisibility(View.VISIBLE);
            btToggleLauncher.setVisibility(View.VISIBLE);
        }
    }

    public boolean hasSecondAppStarted()
    {
        return has_second_app_started;
    }

    public boolean hasPlayerStarted()
    {
        return has_player_started;
    }

    public void toggleLockTask(View view)
    {
        if (MyKiosk.toggleKioskMode())
        {
            btToggleLock.setText(R.string.unpin_app);
        }
        else
        {
            btToggleLock.setText(R.string.pin_app);
       }
    }

    public void toggleLauncher(View view)
    {
        if (MyKiosk.toggleHomeActivity())
        {
            btToggleLauncher.setText(R.string.restore_old_launcher);
        }
        else
        {
            btToggleLauncher.setText(R.string.become_launcher);
        }
    }

    public void toggleServiceMode(View view)
    {
        stopPlayerRestart(); // otherwise we start the countdown multiple times when recreate

        final AlertDialog.Builder alert = new AlertDialog.Builder(this);
        alert.setTitle("Service Login");
        alert.setMessage("Enter your password");
        final EditText input = new EditText(this);
        alert.setView(input);

        alert.setPositiveButton("Ok", new DialogInterface.OnClickListener()
        {
            public void onClick(DialogInterface dialog, int whichButton)
            {
                String value = input.getText().toString();

                // we need an alternative for those one who forget passwords
                // so we get the device UUID via
                // String alt_password = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
                // or set something own
                String alt_password = "heidewitzka";
                if (value.equals(MySharedConfiguration.getServicePassword()) || value.equals(alt_password))
                {
                    if (MyKiosk.isStrictKioskModeActive())
                    {
                        MyKiosk.toggleServiceMode(true);
                        btToggleServiceMode.setText(R.string.exit_service_mode);
                        MyDeviceOwner.activateRestrictions();
                    }
                    else
                    {
                        MyKiosk.toggleServiceMode(false);
                        btToggleServiceMode.setText(R.string.enter_service_mode);
                        MyDeviceOwner.deactivateRestrictions();
                    }
                    recreate();
                }
                else
                {
                    startGarlicPlayerDelayed();
                }
            }
        });
        alert.show();
    }

    public void startGarlicPlayerDelayed()
    {
        has_second_app_started = false;
        has_player_started     = false;

        if (MySharedConfiguration.getSmilIndex("").isEmpty() || !Network.isConnected(this) || !PlayerDownload.isGarlicPlayerInstalled(this))
        {
            btStartPlayer.setText(R.string.play);
            return;
        }
        if (is_countdown_running) // prevent running countdown multiple times
        {
            return;
        }
        PlayerCountDown      = new CountDownTimer(15000, 1000)
        {
            public void onTick(long millisUntilFinished)
            {
                btStartPlayer.setText(getString(R.string.count_down, String.valueOf(millisUntilFinished / 1000)));
                is_countdown_running = true;
            }

            public void onFinish()
            {
                btStartPlayer.setText(R.string.play);
                is_countdown_running = false;

                startGarlicPlayer(null);
            }

        }.start();
    }

    public void handleGarlicPlayerStartTimer(View view)
    {
        if (!btStartPlayer.getText().equals(getResources().getString(R.string.play)))
        {
            if (PlayerCountDown != null)
            {
                stopPlayerRestart();
            }
        }
        else
        {
            startGarlicPlayer(view);
        }
    }

    public void setContentUrl(View view)
    {
        stopPlayerRestart();
        startActivity(new Intent(this, ContentUrlActivity.class));
    }

    public void configAdmin(View view)
    {
        stopPlayerRestart();
        startActivity(new Intent(this, ActivityConfigAdmin.class));
    }

    public void configWiFi(View view)
    {
        stopPlayerRestart();
        startActivityForResult(new Intent(android.net.wifi.WifiManager.ACTION_PICK_WIFI_NETWORK), 0);
    }

    public void startGarlicPlayer(View view)
    {
        has_second_app_started = false;
        has_player_started     = true;
        NavigationBar.hide(this, MySharedConfiguration);
        startApp(DeviceOwner.GARLIC_PLAYER_PACKAGE_NAME);
    }

    public void startSecondApp(String package_name)
    {
        has_second_app_started = true;
        has_player_started     = false;
        NavigationBar.show(this, MySharedConfiguration);
        MyDeviceOwner.determinePermittedLockTaskPackages(package_name);
        startApp(package_name);
    }

    private void startApp(String package_name)
    {
        Intent intent = getPackageManager().getLaunchIntentForPackage(package_name);
        startActivity(intent);
    }

    private void stopPlayerRestart()
    {
        has_second_app_started = false;
        has_player_started     = false;
        is_countdown_running   = false;
        if (PlayerCountDown != null)
        {
            PlayerCountDown.cancel();
        }
        btStartPlayer.setText(R.string.play);
    }

    private void displayInformationText(String error_text)
    {
        tvInformation.setText(error_text);
        tvInformation.setVisibility(View.VISIBLE);
    }

    private void hideInformationText()
    {
        tvInformation.setText("");
        tvInformation.setVisibility(View.INVISIBLE);
    }}
