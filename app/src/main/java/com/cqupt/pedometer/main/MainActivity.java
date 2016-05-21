package com.cqupt.pedometer.main;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import com.cqupt.pedometer.R;
import com.cqupt.pedometer.setting.Choose;
import com.cqupt.pedometer.setting.Input;
import com.cqupt.pedometer.welcome.SetActivity;

import java.text.DecimalFormat;

public class MainActivity extends Activity {
    private static final int REQUEST_CHANGE_NAME = 2;
    private TextView step, distance, calorie, tv_bluetooth_name, tv_battery, tv_rssi;
    private Button connect, setting;
    private BluetoothDevice mDevice = null;
    private static final int UART_PROFILE_DISCONNECTED = 21;
    private int mState = UART_PROFILE_DISCONNECTED;
    private UartService mService = null;
    private String deviceAddress;
    private static final int UART_PROFILE_CONNECTED = 20;
    private SharedPreferences sharedPreferences;
    private String rssi;
    private boolean isManualDisconnect;

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
        }
        setContentView(R.layout.activity_main);
        Intent intent = this.getIntent();        //获取已有的intent对象
        Bundle bundle = intent.getExtras();    //获取intent里面的bundle对象
        deviceAddress = bundle.getString(BluetoothDevice.EXTRA_DEVICE);
        rssi = bundle.getString("rssi");
        tv_rssi = ((TextView) findViewById(R.id.tv_rssi));
        mDevice = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(deviceAddress);
        service_init();
        tv_bluetooth_name = (TextView) findViewById(R.id.tv_bluetooth_name);
        tv_bluetooth_name.setText(getString(R.string.bluetooth_name) + mDevice.getName());
        tv_battery = (TextView) findViewById(R.id.tv_battery);
        step = (TextView) findViewById(R.id.step);
        distance = (TextView) findViewById(R.id.distance);
        calorie = (TextView) findViewById(R.id.calorie);
        connect = (Button) findViewById(R.id.connect);
        setting = (Button) findViewById(R.id.setting);
        connect.setOnClickListener(new OnClickListener());
        setting.setOnClickListener(new OnClickListener());
        try {
            sharedPreferences = this.getSharedPreferences(Input.MY_DATA,
                    Context.MODE_PRIVATE);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public class OnClickListener implements View.OnClickListener {

        @Override
        public void onClick(View v) {
            if (v == connect) {
                if (connect.getText().equals(getString(R.string.connect))) {
                    CommonTools.showShortToast(MainActivity.this, getString(R.string.tryReconnet));
                    mService.connect(deviceAddress);
                } else {
                    if (mDevice != null) {
                        mService.disconnect();
                    }
                    skipWelcomeActivity();
                }
            } else if (v == setting) {
                openSetting();
            }
        }
    }

    private void skipWelcomeActivity() {
        isManualDisconnect = true;
        Intent newIntent = new Intent(MainActivity.this, WelcomeActivity.class);
        Bundle bundle = new Bundle(); //创建Bundle对象
        bundle.putBoolean(WelcomeActivity.M2W, false);     //装入数据
        newIntent.putExtras(bundle);
        startActivity(newIntent);
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    /**
     * 广播接收器
     */
    private final BroadcastReceiver UARTStatusChangeReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            /**
             * 连接成功
             */
            if (action.equals(UartService.ACTION_GATT_CONNECTED)) {
                connect.setText(R.string.disconnect);
                tv_bluetooth_name.setText(getString(R.string.bluetooth_name) + mDevice.getName());
                mState = UART_PROFILE_CONNECTED;
                tv_rssi.setText(getString(R.string.rssi) + rssi + "dBm");
                tv_battery.setText(getString(R.string.fullBattery));
            }
            /**
             * 连接断开
             */
            if (action.equals(UartService.ACTION_GATT_DISCONNECTED)) {
                if (isManualDisconnect) {
                    return;
                }
                connect.setText(R.string.connect);
                tv_bluetooth_name.setText(R.string.no_bt);
                tv_battery.setText(getString(R.string.battery));
                tv_rssi.setText(R.string.rssi_null);
                mService.connect(deviceAddress);
                mState = UART_PROFILE_DISCONNECTED;
            }
            /**
             * 获取数据
             */
            double stepLength;
            int w0 = Integer.parseInt(sharedPreferences.getString(SetActivity.SET_HEIGHT, "160"));
            if (w0 < 150) {
                stepLength = 0.4;
            } else if (w0 < 160) {
                stepLength = 0.5;
            } else {
                stepLength = 0.6;
            }
            int w1 = Integer.parseInt(sharedPreferences.getString(SetActivity.SET_WEIGHT, "50"));
            int w2 = 80;
            if (sharedPreferences.getString(Choose.GENDER, getString(R.string.male)).equals(getString(R.string.male))) {
                w2 = 100;
            }
            double calorie_value;
            int stepValue;
            DecimalFormat df = new DecimalFormat("######0.000");
            if (action.equals(UartService.ACTION_DATA_AVAILABLE)) {
                String s_Value = intent.getStringExtra(UartService.EXTRA_DATA);
                if (TextUtils.isEmpty(s_Value)) {
                    return;
                }
                stepValue = Integer.parseInt(s_Value);
//              接受到CHAR4的计步数据
                step.setText(getString(R.string.step) + "\n" + stepValue+ "步");
                calorie_value = 3.330416666666667E-008D * w0 * w1 * w2 * stepValue;
                calorie.setText(getString(R.string.calorie) + "\n" + df.format(calorie_value) + "大卡");
                distance.setText(getString(R.string.distance) + "\n" + df.format(stepValue * stepLength) + "米");

                //获取RSSI
                final String rssiStatus = intent.getStringExtra(UartService.RSSI_STATUS);
                if (!TextUtils.isEmpty(rssiStatus)) {
                    if (rssiStatus.equals("0")) {
                        rssi = intent.getStringExtra(UartService.RSSI);
                        tv_rssi.setText(getString(R.string.rssi) + rssi + "dBm");
                    }
                }
                //写数据是否成功
                final String writeStatus = intent.getStringExtra(UartService.WRITE_STATUS);
                if (!TextUtils.isEmpty(writeStatus)) {
                    //写数据未成功
                    if (!writeStatus.equals("0")) {
                        //重新获取门锁的状态
                        mService.readCharacteristic(UartService.RX_SERVICE_UUID, UartService.RX_CHAR_UUID);
                    }
                }
            }
            /**
             * 获取电量
             */
            if (action.equals(UartService.EXTRAS_DEVICE_BATTERY)) {
                final String txValue = intent.getStringExtra(UartService.EXTRA_DATA);
                tv_battery.setText(getString(R.string.battery_value) + txValue + "%");
            }
            /**
             * 发现服务后 发起获取通知数据的请求
             */
            if (action.equals(UartService.ACTION_GATT_SERVICES_DISCOVERED)) {
                try {
                    mService.enableTXNotification();
                    //获取电量
                    mService.readCharacteristic(UartService.Battery_Service_UUID, UartService.Battery_Level_UUID);
                    //获取门锁的状态
                    mService.readCharacteristic(UartService.RX_SERVICE_UUID, UartService.RX_CHAR_UUID);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    };

    private void openSetting() {
        Intent newIntent = new Intent(MainActivity.this, SettingActivity.class);
        startActivityForResult(newIntent, REQUEST_CHANGE_NAME);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
            if (mState == UART_PROFILE_CONNECTED) {
                Intent startMain = new Intent(Intent.ACTION_MAIN);
                startMain.addCategory(Intent.CATEGORY_HOME);
                startMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(startMain);
                CommonTools.showShortToast(MainActivity.this, getString(R.string.Sign_out));
            } else {
                finish();
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            }
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_MENU) { //监控/拦截菜单键
            openSetting();
            return true;
        } else {
            return super.onKeyDown(keyCode, event);
        }
    }

    /**
     * 服务中间人
     */
    private ServiceConnection mServiceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder rawBinder) {
            mService = ((UartService.LocalBinder) rawBinder).getService();
            if (!mService.initialize()) {
                finish();
            } else {
                //服务开启后 连接蓝牙
                mService.connect(deviceAddress);
            }
        }

        public void onServiceDisconnected(ComponentName classname) {
            mService = null;
        }
    };


    /**
     * 开启服务
     * 注册广播
     */
    private void service_init() {
        Intent bindIntent = new Intent(this, UartService.class);
        bindService(bindIntent, mServiceConnection, Context.BIND_AUTO_CREATE);
        LocalBroadcastManager.getInstance(this).registerReceiver(UARTStatusChangeReceiver, makeGattUpdateIntentFilter());
    }

    /**
     * 广播过滤器
     *
     * @return
     */
    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(UartService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(UartService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(UartService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(UartService.ACTION_DATA_AVAILABLE);
        intentFilter.addAction(UartService.DEVICE_DOES_NOT_SUPPORT_UART);
        intentFilter.addAction(UartService.EXTRAS_DEVICE_BATTERY);
        return intentFilter;
    }

    /**
     * 当破坏Activity时调用
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mService.stopSelf();
        mService = null;
    }
}
