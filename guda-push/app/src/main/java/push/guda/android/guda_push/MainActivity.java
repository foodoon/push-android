package push.guda.android.guda_push;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Toast;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

import guda.push.connect.protocol.api.Command;
import guda.push.connect.protocol.api.Field;
import guda.push.connect.protocol.api.Struct;
import guda.push.connect.protocol.codec.CodecUtil;
import guda.push.connect.protocol.codec.tlv.TLV;
import guda.push.connect.protocol.codec.tlv.TypeConvert;
import guda.push.connect.queue.MsgFactory;
import guda.push.connect.queue.WaitAckFactory;
import push.guda.android.guda_push.service.CmdService;
import push.guda.android.guda_push.thread.UdpRetryThread;


public class MainActivity extends ActionBarActivity {

    private EditText server_host;
    private EditText server_port;
    private EditText client_port;
    private RadioGroup conn_style;


    private EditText user_id;
    private Button btn_login;
    private EditText target_user_id;
    private EditText content;
    private Button btn_send;


    private Handler handler;
    private Runnable refresher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        SharedPreferences account = this.getSharedPreferences(Params.KEY, Context.MODE_PRIVATE);
        server_host = (EditText) findViewById(R.id.server_host);
        server_host.setText(account.getString(Params.SERVER_HOST, "192.168.1.103"));
        server_port = (EditText) findViewById(R.id.server_port);
        server_port.setText(account.getString(Params.SERVER_PORT, "10085"));
        conn_style =  (RadioGroup)findViewById(R.id.conn_style);
        client_port = (EditText) findViewById(R.id.client_port);
        client_port.setText(account.getString(Params.CLIENT_PORT, "10085"));
        target_user_id = (EditText) findViewById(R.id.target_user_id);
        content = (EditText) findViewById(R.id.content);
        user_id = (EditText) findViewById(R.id.user_id);
        user_id.setText(String.valueOf(account.getString(Params.USER_ID, "1")));

        btn_login = (Button) findViewById(R.id.btn_login);
        btn_login.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                MainActivity.this.start();
            }
        });
        btn_send = (Button) findViewById(R.id.btn_send);
        btn_send.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                MainActivity.this.sendMsg();

            }
        });

        Intent startSrv = new Intent(this.getApplicationContext(), CmdService.class);
        this.getApplicationContext().startService(startSrv);
        new UdpRetryThread();
    }

    protected void sendMsg() {
        String txt_content = content.getText().toString();
        if (txt_content.length() == 0) {
            Toast.makeText(this.getApplicationContext(), "请输入一串文字", Toast.LENGTH_SHORT).show();
            content.requestFocus();
            return;
        }
        if (target_user_id.getText().toString().length() == 0) {
            Toast.makeText(this.getApplicationContext(), "请输入目标用户名", Toast.LENGTH_SHORT).show();
            target_user_id.requestFocus();
            return;
        }
        SharedPreferences account = this.getSharedPreferences(Params.KEY, Context.MODE_PRIVATE);
        String serverIp = account.getString(Params.SERVER_HOST, "");
        int serverPort = getInt(account.getString(Params.SERVER_PORT, ""));
        long userId = getLong(account.getString(Params.USER_ID, ""));
        int port;
        if(serverPort == 0){
            Toast.makeText(this.getApplicationContext(), "端口格式错误：" + serverPort, Toast.LENGTH_SHORT).show();
            return;
        }
        long targetUserId = 0;
        try {
            targetUserId  = Long.parseLong(target_user_id.getText().toString());
            if (targetUserId == 0) {
                Toast.makeText(this.getApplicationContext(), "接收方用户应该是整数：" + serverPort, Toast.LENGTH_SHORT).show();
                return;
            }
        }catch(Exception e) {

        }
        if(userId ==0){
            Toast.makeText(this.getApplicationContext(), "接收方用户应该是整数：" + serverPort, Toast.LENGTH_SHORT).show();
            return;
        }
        Thread t = new Thread(new sendTask(this, serverIp, serverPort, targetUserId,userId,txt_content));
        t.start();

    }

    class sendTask implements Runnable {
        private Context context;
        private String serverIp;
        private int port;
        private long targetUserId;
        private long userId;
        private String content;

        public sendTask(Context context, String serverIp, int port, long targetUserId,long userId,String content) {
            this.context = context;
            this.serverIp = serverIp;
            this.port = port;
            this.targetUserId = targetUserId;
            this.userId = userId;
            this.content = content;
        }

        public void run() {
            DatagramSocket ds = null;
            Intent startSrv = new Intent(context, CmdService.class);
            try {

                startSrv.putExtra("CMD", "TOAST");
                TLV tlv = CodecUtil.newTlv(Struct.CHAT);
                tlv.add(new TLV(Field.CMD, TypeConvert.int2byte(Command.CHAT)));
                tlv.add(new TLV(Field.CHAT_CONTENT, TypeConvert.string2byte(content)));
                tlv.add(new TLV(Field.FROM_USER, TypeConvert.long2byte(userId)));
                tlv.add(new TLV(Field.TO_USER, TypeConvert.long2byte(targetUserId)));
                ds = new DatagramSocket();
                byte[] bytes = tlv.toBinary();
                DatagramPacket dp = new DatagramPacket(bytes, bytes.length, InetAddress
                        .getByName(serverIp), port);
                ds.send(dp);
                startSrv.putExtra("TEXT", "信息发送成功");
                long seq = CodecUtil.findTagLong(tlv,Field.SEQ);
                WaitAckFactory.add(seq,tlv);
            }catch(Exception e){
e.printStackTrace();
            }finally{
                if(ds!=null){
                    try {
                    ds.close();
                    } catch (Exception e) {
                    }
                }
            }

            context.startService(startSrv);
        }
    }

    protected void start() {
        if (server_host.getText().toString().length() == 0) {
            Toast.makeText(this.getApplicationContext(), "请输入服务器ip", Toast.LENGTH_SHORT).show();
            server_host.requestFocus();
            return;
        }
        if (server_port.getText().toString().length() == 0) {
            Toast.makeText(this.getApplicationContext(), "请输入服务器端口", Toast.LENGTH_SHORT).show();
            server_port.requestFocus();
            return;
        }
        if (client_port.getText().toString().length() == 0) {
            Toast.makeText(this.getApplicationContext(), "请输入本地端口", Toast.LENGTH_SHORT).show();
            client_port.requestFocus();
            return;
        }

        if (user_id.getText().toString().length() == 0) {
            Toast.makeText(this.getApplicationContext(), "请输入用户名", Toast.LENGTH_SHORT).show();
            user_id.requestFocus();
            return;
        }
        int intServerPort = 0, int_client_port = 0;
        try {
            intServerPort = Integer.parseInt(server_port.getText().toString());
        } catch (Exception e) {
            Toast.makeText(this.getApplicationContext(), "端口格式错误", Toast.LENGTH_SHORT).show();
            server_port.requestFocus();
            return;
        }
        try {
            int_client_port = Integer.parseInt(client_port.getText().toString());
        } catch (Exception e) {
            Toast.makeText(this.getApplicationContext(), "端口格式错误", Toast.LENGTH_SHORT).show();
            client_port.requestFocus();
            return;
        }

        saveAccountInfo();
        Intent startSrv = new Intent(this, CmdService.class);
        startSrv.putExtra("CMD", "RESET");
        this.startService(startSrv);
       // freshCurrentInfo();
    }

    protected void saveAccountInfo() {
        SharedPreferences account = this.getSharedPreferences(Params.KEY, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = account.edit();
        editor.putString(Params.SERVER_HOST, server_host.getText().toString());
        editor.putString(Params.SERVER_PORT, (server_port.getText().toString()));
        editor.putString(Params.CLIENT_PORT, (client_port.getText().toString()));
        editor.putString(Params.USER_ID, (user_id.getText().toString()));
        editor.putInt(Params.CONN_STYLE, conn_style.getCheckedRadioButtonId());

        editor.commit();

    }

    private int getInt(String str){
        try{
            return Integer.parseInt(str);
        }catch (Exception e){

        }
        return 0;
    }

    private long getLong(String str){
        try{
            return Long.parseLong(str);
        }catch (Exception e){

        }
        return 0;
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
