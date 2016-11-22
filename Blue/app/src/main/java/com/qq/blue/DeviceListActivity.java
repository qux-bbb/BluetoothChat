package com.qq.blue;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import java.util.Set;

/**
 * 这个 Activity显示为对话框.
 * 它会列出已配对的设备和在该区域检测后发现的设备
 * 当用户选择一个设备后，该设备的Mac地址就会被发送到父Activity
 */

public class DeviceListActivity extends Activity{

    private static final String TAG = "DeviceListActivity";
    public static String EXTRA_DEVICE_ADDRESS = "device_address";

    private BluetoothAdapter mBtAdapter;
    private ArrayAdapter mNewDevicesArrayAdapter;
    private ArrayAdapter mPairedDevicesArrayAdapter;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.device_list);

        // 设置 result为 取消，防止用户返回
        setResult(Activity.RESULT_CANCELED);

        ////初始化按钮点击事件以执行设备发现
        Button scanButton = (Button) findViewById(R.id.button_scan);
        scanButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                doDiscovery();
                v.setVisibility(View.GONE);
            }
        });


        //初始化数组适配器.
        // 一个用于已经配对的设备,一个用于新发现的设备
        mPairedDevicesArrayAdapter = new ArrayAdapter(this, R.layout.device_name);
        mNewDevicesArrayAdapter = new ArrayAdapter(this, R.layout.device_name);

        // 找到和设置已配对设备的ListView
        ListView pairedListView = (ListView)findViewById(R.id.paired_devices);
        pairedListView.setAdapter(mPairedDevicesArrayAdapter);
        pairedListView.setOnItemClickListener(mDeviceClickListener);

        // 找到和设置新发现设备的ListView
        ListView newDevicesListView = (ListView) findViewById(R.id.new_devices);
        newDevicesListView.setAdapter(mNewDevicesArrayAdapter);
        newDevicesListView.setOnItemClickListener(mDeviceClickListener);

        //当发现设备时注册广播
        registerReceiver(mReceiver, new IntentFilter(BluetoothDevice.ACTION_FOUND));

        //发现完成后注册广播
        registerReceiver(mReceiver, new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED));


        mBtAdapter = BluetoothAdapter.getDefaultAdapter();

        //获取一个当前配对的设备的集合
        Set<BluetoothDevice> pairedDevices = mBtAdapter.getBondedDevices();

        //如果有配对的设备，将每个设备添加到ArrayAdapter
        if(pairedDevices.size() > 0) {
            findViewById(R.id.title_paired_devices).setVisibility(View.VISIBLE);

            for (BluetoothDevice device : pairedDevices) {
                mPairedDevicesArrayAdapter.add(device.getName() + "\n" + device.getAddress());
            }
        }
        else {
            mPairedDevicesArrayAdapter.add(getResources().getText(R.string.none_paired).toString());
        }
    }



    protected void onDestroy() {
        super.onDestroy();
        //确保不再搜寻设备
        if(mBtAdapter != null) {
            mBtAdapter.cancelDiscovery();
        }

        //取消注册广播监听器
        this.unregisterReceiver(mReceiver);
    }

    /**
     *使用BluetoothAdapter启动设备搜寻
     */
    private void doDiscovery() {
        Log.d(TAG, "doDiscovery()");

        //在标题中显示扫描状态
        setProgressBarIndeterminateVisibility(true);
        setTitle(R.string.scanning);

        //打开新设备的子标题
        findViewById(R.id.title_new_devices).setVisibility(View.VISIBLE);

        //如果之前已经在搜寻，那就停止
        if(mBtAdapter.isDiscovering()) {
            mBtAdapter.cancelDiscovery();
        }

        mBtAdapter.startDiscovery();
    }

    /**
     * ListView中所有设备的点击监听器
     */
    private AdapterView.OnItemClickListener mDeviceClickListener
            = new AdapterView.OnItemClickListener(){
        public void onItemClick(AdapterView av, View v, int arg2, long arg3) {

            //取消搜寻，因为消耗资源
            mBtAdapter.cancelDiscovery();

            //获取设备的MAC地址，也就是最后17个字符
            String info = ((TextView)v).getText().toString();
            String address = info.substring(info.length() - 17);

            //创建结果Intent并包含MAC地址
            Intent intent = new Intent();
            intent.putExtra(EXTRA_DEVICE_ADDRESS, address);

            //设置结果并结束 Activity
            setResult(Activity.RESULT_OK, intent);
            finish();
        }
    };

    /**
     * 用于监听已搜索到的设备，在搜寻完成后改变标题
     */
    private final BroadcastReceiver mReceiver = new BroadcastReceiver(){

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            // 搜寻到一个设备时
            if(BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

                // 如果已经配对，跳过，因为已经列出
                if(device.getBondState() != BluetoothDevice.BOND_BONDED) {
                    mNewDevicesArrayAdapter.add(device.getName() + "\n" + device.getAddress());
                }
            }
            //搜寻完成后，改变Activity标题
            else if(BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                setProgressBarIndeterminateVisibility(false);
                setTitle(R.string.select_device);
                if(mNewDevicesArrayAdapter.getCount() == 0) {
                    String noDevices = getResources().getText(R.string.none_found).toString();
                    mNewDevicesArrayAdapter.add(noDevices);
                }
            }
        }
    };

}
