package com.qq.blue;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.graphics.YuvImage;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import static android.bluetooth.BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE;
import static android.bluetooth.BluetoothAdapter.STATE_DISCONNECTING;
import static android.view.Window.FEATURE_CUSTOM_TITLE;


public class MainActivity extends Activity{


    private static final String TAG = "BluetoothChat";

    //从BluetoothChatService处理程序接收的键名称
    public static final String DEVICE_NAME = "device_name";
    public static final String TOAST = "toast";

    // 从BluetoothChatService处理程序发送的消息类型
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;

    //意图请求代码
    private static final int REQUEST_CONNECT_DEVICE = 1;
    private static final int REQUEST_ENABLE_BT = 2;

    // 已连接设备名字
    private String mConnectedDeviceName;

    //用于会话线程的数组适配器
    private ArrayAdapter mConversationArrayAdapter;

    //本地蓝牙适配器
    private BluetoothAdapter mBluetoothAdapter;

    // 发送消息的字符串缓冲区
    private StringBuffer mOutStringBuffer;

    private BluetoothChatService mChatService;

    //布局视图
    private EditText mOutEditText;
    private ListView mConversationView;
    private Button mSendButton;

    private TextView mTitle;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.e(TAG, "onCreate");
        //设置自定义标题栏
        requestWindowFeature(FEATURE_CUSTOM_TITLE);
        setContentView(R.layout.activity_main);
        getWindow().setFeatureInt(FEATURE_CUSTOM_TITLE, R.layout.custom_title);


        // 设置标题
        mTitle = (TextView) findViewById(R.id.title_left_text);
        mTitle.setText("Blue");

        mTitle = (TextView) findViewById(R.id.title_right_text);
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        // 判断蓝牙是否可用
        if (mBluetoothAdapter == null) {
            Toast.makeText(MainActivity.this, "蓝牙不可用", Toast.LENGTH_LONG).show();
            finish();
        }

    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.e(TAG, "onStart");
        // 开启蓝牙
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        } else if (mChatService == null) {
            setupChat();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.e(TAG, "onPause");
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.e(TAG, "onResume");
        if (mChatService != null && mChatService.getState() == BluetoothChatService.STATE_NONE) {
            mChatService.start();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.e(TAG, "onStop");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mChatService != null) {
            mChatService.stop();
        }
        Log.e(TAG, "onDestroy");
    }


    // 菜单设置
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.option_menu, menu);
        return true;
    }


    /**
     * 菜单选项：连接到设备，使本机蓝牙可检测到
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.scan:
                startActivityForResult(new Intent(MainActivity.this, DeviceListActivity.class),REQUEST_CONNECT_DEVICE);
                return true;
            case R.id.discoverable:
                ensureDiscoverable();
                return true;
        }
        return true;
    }


    private void setupChat() {
        Log.d(TAG, "setupChat()");

        //初始化会话线程的数组适配器
        mConversationArrayAdapter = new ArrayAdapter(MainActivity.this, R.layout.message);

        mConversationView = (ListView) findViewById(R.id.in);
        mConversationView.setAdapter(mConversationArrayAdapter);

        mOutEditText = (EditText) findViewById(R.id.edit_text_out);
        mOutEditText.setOnEditorActionListener(mWriteListener);

        mSendButton = (Button) findViewById(R.id.button_send);
        mSendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //发送编辑框的内容
                sendMessage(((TextView) findViewById(R.id.edit_text_out)).getText().toString());
            }
        });

        //初始化BluetoothChatService以执行蓝牙连接
        mChatService = new BluetoothChatService(MainActivity.this, mHandler);

        //初始化外发消息的缓冲区
        mOutStringBuffer = new StringBuffer("");
    }


    // 使本设备可被检测到
    private void ensureDiscoverable() {
        Log.d(TAG, "ensure discoverable");
        if (mBluetoothAdapter.getScanMode() != SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
            startActivity(discoverableIntent);
        }
    }


    private void sendMessage(String message) {
        //检测是否已连接
        if (mChatService.getState() != BluetoothChatService.STATE_CONNECTED) {
            Toast.makeText(MainActivity.this, R.string.not_connected, Toast.LENGTH_SHORT).show();
        } else if (message.length() > 0) {
            //获取消息字节并通知BluetoothChatService写入
            mChatService.write(message.getBytes());

            //将发送消息缓冲区重置为零并清除编辑消息框
            mOutStringBuffer.setLength(0);
            mOutEditText.setText(mOutStringBuffer);
        }
    }


    // 对startActivityForResult()的返回结果处理
    public void onActivityResult(int requestCode, int resultCode, Intent data) {


        Log.d(TAG, "onActivityResult " + resultCode);
        switch (requestCode) {
            case REQUEST_CONNECT_DEVICE: {
                if (resultCode == RESULT_OK) {
                    mChatService.connect(mBluetoothAdapter.getRemoteDevice(data.getExtras()
                            .getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS)));
                }
                break;
            }
            case REQUEST_ENABLE_BT: {
                if (resultCode == RESULT_OK) {
                    setupChat();
                }else{
                    Log.d(TAG, "BT not enabled");
                    Toast.makeText(MainActivity.this, R.string.bt_not_enabled_leaving,
                            Toast.LENGTH_SHORT).show();
                    finish();
                }
            }
        }
    }


    /**
     * EditText的动作侦听器，监听回车键
     */
    private TextView.OnEditorActionListener mWriteListener
            =new TextView.OnEditorActionListener(){
        @Override
        public boolean onEditorAction(TextView view, int actionId, KeyEvent event) {
            // 按下回车键也可以发送信息，ACTION_UP捕捉不到
            if (actionId == EditorInfo.IME_NULL && event.getAction() == KeyEvent.ACTION_DOWN) {
                String message = view.getText().toString();
                sendMessage(message);
            }
            Log.i(TAG,"End onEditorAction");
            return true;
        }
    };


    /**
     * 从BluetoothChatService获取信息的处理程序
     */
    private final Handler mHandler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_STATE_CHANGE: {
                    Log.i(TAG, "MESSAGE_STATE_CHANGE: " + msg.arg1);
                    switch (msg.arg1) {
                        case BluetoothChatService.STATE_CONNECTED: {
                            mTitle.setText(R.string.title_connected_to);
                            mTitle.setTextColor(0xff11ff11);
                            mTitle.append(mConnectedDeviceName);
                            mConversationArrayAdapter.clear();
                            break;
                        }
                        case BluetoothChatService.STATE_CONNECTING: {
                            mTitle.setText(R.string.title_connecting);
                            mTitle.setTextColor(0xff222222);
                            break;
                        }
                        case BluetoothChatService.STATE_LISTEN:
                        case BluetoothChatService.STATE_NONE: {
                            mTitle.setText(R.string.title_not_connected);
                            mTitle.setTextColor(0xffff1111);
                            break;
                        }
                    }
                    break;
                }
                case MESSAGE_WRITE: {
                    byte[] writeBuf = (byte[]) msg.obj;
                    String writeMessage = new String(writeBuf);
                    mConversationArrayAdapter.add("Me:  " + writeMessage);
                    break;
                }
                case MESSAGE_READ: {
                    byte[] readBuf = (byte[]) msg.obj;
                    String readMessage = new String(readBuf, 0, msg.arg1);
                    mConversationArrayAdapter.add(mConnectedDeviceName + ":  " + readMessage);
                    break;
                }

                case MESSAGE_DEVICE_NAME: {
                    mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
                    Toast.makeText(getApplicationContext(), "Connected to " + mConnectedDeviceName,
                            Toast.LENGTH_SHORT).show();
                    break;
                }
                case MESSAGE_TOAST: {
                    Toast.makeText(getApplicationContext(), msg.getData().getString(TOAST),
                            Toast.LENGTH_SHORT).show();
                    break;
                }
            }
        }
    };


}



