package com.qq.blue;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

public class BluetoothChatService {

    private static final String TAG = "BluetoothChatService";

    // UUID
    private static UUID MY_UUID = UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66");

    private static final String NAME = "BluetoothChat";

    private final BluetoothAdapter mAdapter;
    private final Handler mHandler;
    private AcceptThread mAcceptThread;
    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;
    private int mState;

    //表示当前连接状态的常量
    public static final int STATE_NONE = 0;         //什么也不做
    public static final int STATE_LISTEN = 1;       //监听传入连接
    public static final int STATE_CONNECTING = 2;   //与远程设备连接
    public static final int STATE_CONNECTED = 3;    //已连接到远程设备


    /**
     *构造函数  准备新的BluetoothChat会话
     * @param context
     * @param handler  设置界面状态的处理程序
     */
    public BluetoothChatService(Context context,Handler handler){
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mState = STATE_NONE;
        mHandler = handler;
    }

    /**
     * 设置聊天连接的当前状态
     * @param aState    定义当前连接状态的整数
     */
    private synchronized void setState(int aState) {
        Log.d(TAG, "setState() " + mState + " -> " + aState);
        mState = aState;
        //将新状态赋予处理程序，以便UI活动可以更新
        mHandler.obtainMessage(MainActivity.MESSAGE_STATE_CHANGE, aState, -1).sendToTarget();
    }

    // 返回当前连接状态
    public synchronized int getState() {
        return mState;
    }

    /**
     *启动聊天服务.启动AcceptThread开启一个在侦听（服务器）模式下的会话。由Activity onResume（） 调用
     */
    public synchronized void start() {
        Log.d(TAG, "start");

        //取消尝试建立连接的任何线程
        if(mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        //取消当前运行连接的任何线程
        if(mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        setState(STATE_LISTEN);

        //启动线程以监听BluetoothServerSocket
        if(mAcceptThread == null) {
            mAcceptThread = new AcceptThread();
            mAcceptThread.start();
        }

    }

    /**
     * 启动ConnectThread以启动与远程设备的连接.
     * @param bluetoothDevice
     */
    public synchronized void connect(BluetoothDevice bluetoothDevice) {
        Log.d(TAG, "connect to: " + bluetoothDevice);

        //取消尝试建立连接的任何线程
        if(mState == STATE_CONNECTING && mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        //取消当前运行连接的任何线程
        if(mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        //启动线程来连接给定设备
        mConnectThread = new ConnectThread(bluetoothDevice);
        mConnectThread.start();
        setState(STATE_CONNECTING);
    }

    /**
     * 启动ConnectedThread以开始管理蓝牙连接
     * @param bluetoothSocket
     * @param bluetoothDevice   已连接的设备
     */
    public synchronized void connected(BluetoothSocket bluetoothSocket, BluetoothDevice bluetoothDevice) {
        Log.d(TAG, "connected");

        ////取消完成连接的线程
        if(mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        //取消当前运行连接的任何线程
        if(mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        //取消接受线程，因为只需要连接到一个设备
        if(mAcceptThread != null) {
            mAcceptThread.cancel();
            mAcceptThread = null;
        }

        //启动线程以管理连接并执行传输
        mConnectedThread = new ConnectedThread(bluetoothSocket);
        mConnectedThread.start();

        //将连接的设备的名称发送回UI Activity
        Message message = mHandler.obtainMessage(MainActivity.MESSAGE_DEVICE_NAME);
        Bundle bundle = new Bundle();
        bundle.putString(MainActivity.DEVICE_NAME, bluetoothDevice.getName());
        message.setData(bundle);
        mHandler.sendMessage(message);

        setState(STATE_CONNECTED);
    }


    // 停止所有的线程
    public synchronized void stop() {
        Log.d(TAG, "stop");

        if(mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        if(mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        if(mAcceptThread != null) {
            mAcceptThread.cancel();
            mAcceptThread = null;
        }

        setState(STATE_NONE);
    }

    // 写消息
    public void write(byte[] message) {
        ConnectedThread connectedThread;
        synchronized(this){
            if(mState != STATE_CONNECTED) return;
            connectedThread = mConnectedThread;
        }
        connectedThread.write(message);
    }

    /**
     * 提示连接尝试失败并通知 UI Activity
     */
    private void connectionFailed() {
        //发送失败消息到 Activity
        Message message = mHandler.obtainMessage(MainActivity.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(MainActivity.TOAST, "Unable to connect device");
        message.setData(bundle);
        mHandler.sendMessage(message);

        //启动服务以重新启动监听模式
        start();
    }

    /**
     * 提示连接丢失并通知UI Activity
     */
    private void connectionLost() {
        // 发送失败消息到 Activity
        Message message = mHandler.obtainMessage(MainActivity.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(MainActivity.TOAST, "Device connection was lost");
        message.setData(bundle);
        mHandler.sendMessage(message);

        //启动服务以重新启动监听模式
        start();
    }

    /**
     * 此线程在监听传入连接时运行.它的行为像服务器端客户端.
     * 直到接受连接或取消连接才停止
     */
    private class AcceptThread extends Thread {
        private final BluetoothServerSocket mmServerSocket;

        public AcceptThread() {
            super();
            BluetoothServerSocket bluetoothServerSocket = null;

            try {
                bluetoothServerSocket = mAdapter.listenUsingRfcommWithServiceRecord(NAME, BluetoothChatService.MY_UUID);
            }
            catch(IOException e) {
                Log.e(TAG, "listen() failed", e);
            }

            mmServerSocket = bluetoothServerSocket;
        }

        public void run() {
            BluetoothSocket bluetoothSocket = null;
            Log.d(TAG, "BEGIN mAcceptThread" + this);
            setName("AcceptThread");

            ////如果没有连接，就监听服务器socket
            //bugbugbug
            while(mState != STATE_CONNECTED) {
                try {
                    //这是一个阻塞调用，只会返回一个成功连接或异常
                    bluetoothSocket = mmServerSocket.accept();
                }
                catch(IOException e) {
                    Log.e(TAG, "accept() failed,ignore this，it will accept again", e);
                    break;
                }

                if (bluetoothSocket != null){
                    synchronized (BluetoothChatService.this){
                        switch(mState) {
                            //情况正常. 启动连接的线程.
                            case STATE_LISTEN:
                            case STATE_CONNECTING: {
                                connected(bluetoothSocket, bluetoothSocket.getRemoteDevice());
                                break;
                            }

                            //未准备就绪或已连接. 终止新socket
                            case STATE_NONE:
                            case STATE_CONNECTED: {
                                try {
                                    bluetoothSocket.close();
                                }catch(IOException e) {
                                    Log.e(TAG, "Could not close unwanted socket", e);
                                }
                                break;
                            }
                        }
                    }
                }
            }
            Log.i(TAG, "END mAcceptThread");
        }


        //取消
        public void cancel() {

            Log.d(TAG, "cancel " + this);
            try {
                mmServerSocket.close();
            }
            catch(IOException e) {
                Log.e(TAG, "close() of server failed", e);
            }
        }

    }

    /**
     * 此线程在尝试与设备建立传出连接时运行
     */
    private class ConnectThread extends Thread {
        private final BluetoothDevice mmDevice;
        private final BluetoothSocket mmSocket;

        public ConnectThread(BluetoothDevice bluetoothDevice) {
            BluetoothSocket bluetoothSocket = null;
            mmDevice = bluetoothDevice;

            try {
                bluetoothSocket = bluetoothDevice.createRfcommSocketToServiceRecord(MY_UUID);
            }
            catch(IOException e) {
                Log.e(TAG, "create() failed", e);
            }

            mmSocket = bluetoothSocket;
        }

        public void run() {
            Log.i(TAG, "BEGIN mConnectThread");
            setName("ConnectThread");
            //应该取消蓝牙设备可见，因为它会减慢连接速度
            mAdapter.cancelDiscovery();
            try {
                //这是一个阻塞调用，只会返回一个成功连接或异常
                mmSocket.connect();
            }
            catch(IOException e) {
                try {
                    mmSocket.close();
                }
                catch(IOException e1) {
                    Log.e(TAG, "unable to close() socket during connection failure", e1);
                }

                connectionFailed();
                return;
            }

            // 重置ConnectThread，因为已经完成了
            synchronized (BluetoothChatService.this){
                mConnectThread = null;
            }

            // 开始 connected 线程
            connected(mmSocket, mmDevice);
        }

        public void cancel() {
            try {
                mmSocket.close();
            }catch(IOException e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }
    }


    /**
     * 此线程在与远程设备的连接期间运行
     * 它处理所有传入和传出传输
     */
    private class ConnectedThread extends Thread {
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;
        private final BluetoothSocket mmSocket;

        public ConnectedThread(BluetoothSocket bluetoothSocket) {
            Log.d(TAG, "create ConnectedThread");
            OutputStream outputStream = null;
            InputStream inputStream = null;
            mmSocket = bluetoothSocket;

            try {
                inputStream = bluetoothSocket.getInputStream();
                outputStream = bluetoothSocket.getOutputStream();
            }
            catch(IOException e) {
                Log.e(TAG, "temp sockets not created", e);
            }

            mmInStream = inputStream;
            mmOutStream = outputStream;
        }

        public void run() {
            Log.i(TAG, "BEGIN mConnectedThread");
            byte[] message = new byte[1024];
            while(mState == STATE_CONNECTED){
                try {
                    mHandler.obtainMessage(MainActivity.MESSAGE_READ, mmInStream.read(message), -1, message)
                            .sendToTarget();
                }
                catch(IOException e) {
                    Log.e(TAG, "disconnected", e);
                    connectionLost();

                    break;
                }
            }
        }


        public void write(byte[] message) {
            try {
                mmOutStream.write(message);

                //将发送的消息共享回UI Activity
                mHandler.obtainMessage(MainActivity.MESSAGE_WRITE, -1, -1, message).sendToTarget();
            }
            catch(IOException e) {
                Log.e(TAG, "Exception during write", e);
            }
        }

        public void cancel() {
            try {
                mmSocket.close();
            }
            catch(IOException e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }
    }

}