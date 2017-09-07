package com.android.blepair;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.SimpleAdapter;

import org.apache.commons.lang.StringUtils;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class MainActivity extends AppCompatActivity implements AdapterView.OnItemClickListener{

    private static final String TAG = "MainActivity";
    //UUID：00001101-0000-1000-8000-00805F9B34FB  00001124-0000-1000-8000-00805F9B34FB
    static final String SPP_UUID = "00001101-0000-1000-8000-00805F9B34FB";

    private ListView lv;
    private Button btn,disConnectBtn;
    private List<String> deviceList = new ArrayList<>();
    private ArrayAdapter<String> adapter;

    private BluetoothAdapter bluetoothAdapter;
    BluetoothSocket socket = null;

    Handler handler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            bluetoothAdapter.cancelDiscovery();
            Log.e(TAG,"----停止扫描----");
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        lv = (ListView) findViewById(R.id.listView);
        btn = (Button) findViewById(R.id.searchBtn);
        disConnectBtn = (Button) findViewById(R.id.disconnectBtn);
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        adapter = new ArrayAdapter(MainActivity.this,android.R.layout.simple_list_item_1,deviceList);
        lv.setAdapter(adapter);
        lv.setOnItemClickListener(this);

        // 注册Receiver来获取蓝牙设备相关的结果
        IntentFilter intent = new IntentFilter();
        intent.addAction(BluetoothDevice.ACTION_FOUND);// 用BroadcastReceiver来取得搜索结果
        intent.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        intent.addAction(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED);
        intent.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);   //搜索结束时广播
        intent.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        intent.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        intent.addAction(BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED);
        intent.addAction(BluetoothDevice.ACTION_CLASS_CHANGED);
        intent.addAction(BluetoothDevice.ACTION_PAIRING_REQUEST);
        registerReceiver(searchDevices, intent);


        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //获取已配对设备
                Object[] lstDevice = bluetoothAdapter.getBondedDevices().toArray();
                for(int i = 0;i<lstDevice.length;i++){
                    BluetoothDevice bluetoothDevice = (BluetoothDevice) lstDevice[i];
                    String pairDevice = "已配对|"+ bluetoothDevice.getName()+"-"+bluetoothDevice.getAddress();
                    Log.e(TAG,"----已配对设备--"+pairDevice);
                    deviceList.add(pairDevice);
                    adapter.notifyDataSetChanged();
                }
                bluetoothAdapter.startDiscovery();  //开始搜索
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Thread.sleep(8 * 1000);
                            handler.sendEmptyMessage(111);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }).start();

            }
        });

        disConnectBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
              //  btSocket.getRemoteDevice();
                //new ConnectThread().cancel();
            }
        });
    }

    private BroadcastReceiver searchDevices = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.e(TAG,"------action-----"+action);
            Bundle b = intent.getExtras();
            if(b != null){
                Object[] lstName = b.keySet().toArray();
                // 显示所有收到的消息及其细节
                for (int i = 0; i < lstName.length; i++) {
                    String keyName = lstName[i].toString();
                    Log.e(TAG, String.valueOf(b.get(keyName))+"---"+keyName);
                }
            }

            BluetoothDevice device = null;
            if(BluetoothDevice.ACTION_FOUND.equals(action)){
                device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if(device.getBondState() == BluetoothDevice.BOND_NONE){ //未配对的设备
                      String str = "未配对|"+ device.getName() +"-" +device.getAddress();
                    Log.e(TAG,"----未配对-----"+str);
                    if(deviceList.indexOf(str) == -1){  //防止重复添加
                        deviceList.add(str);
                    }
                    adapter.notifyDataSetChanged();
                }

            }
            else if(BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)){
                device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                switch (device.getBondState()){
                    case BluetoothDevice.BOND_BONDING:
                        Log.e(TAG, "正在配对......");
                        break;
                    case BluetoothDevice.BOND_BONDED:
                        Log.e(TAG, "完成配对");
                        connectDevice(device);//连接设备
                        break;
                    case BluetoothDevice.BOND_NONE:
                        Log.e(TAG, "取消配对");
                    default:
                        break;
                }
            }

        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(searchDevices);
    }

    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
        if(bluetoothAdapter.isDiscovering())
            bluetoothAdapter.cancelDiscovery();
        String str = deviceList.get(i);
        String bleAddress = StringUtils.substringAfter(str,"-");
        Log.e(TAG,"-----address---"+bleAddress);
        BluetoothDevice bd = bluetoothAdapter.getRemoteDevice(bleAddress);
        if(bd.getBondState() == BluetoothDevice.BOND_NONE){ //未绑定
            bd.createBond();    //绑定
        }else if(bd.getBondState() == BluetoothDevice.BOND_BONDED){ //已经绑定
            connectDevice(bd);

        }

    }

    private void connectDevice(BluetoothDevice blud) {
        UUID uuid = UUID.fromString(SPP_UUID);

        Log.e(TAG,"-----开始连接---");
        new connectThread(blud).start();
//        try {
//            socket = blud.createRfcommSocketToServiceRecord(uuid);
//            socket.connect();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }

    }

    private class connectThread extends Thread{
         UUID uuids = UUID.fromString(SPP_UUID);
        private BluetoothDevice device;

        public connectThread(BluetoothDevice device) {
            this.device = device;

        }


        @Override
        public void run() {
            BluetoothSocket tempSocket = null;
            bluetoothAdapter.cancelDiscovery(); //停止扫描
            try {
                Log.e(TAG,"-------开启连接----");
                tempSocket = device.createRfcommSocketToServiceRecord(uuids);
                tempSocket.connect();
            } catch (IOException e) {
                e.printStackTrace();
                Log.e(TAG,"-------连接失败----");
                try {
                    tempSocket.close();
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            }
        }
    }

}
