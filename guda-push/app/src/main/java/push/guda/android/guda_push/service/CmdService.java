package push.guda.android.guda_push.service;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.os.PowerManager;
import android.widget.Toast;

import java.net.DatagramSocket;
import java.net.InetAddress;

import guda.push.connect.protocol.api.Command;
import guda.push.connect.protocol.api.Field;
import guda.push.connect.protocol.codec.CodecUtil;
import guda.push.connect.protocol.codec.tlv.TLV;
import guda.push.connect.queue.WaitAckFactory;
import push.guda.android.guda_push.MainActivity;
import push.guda.android.guda_push.Params;
import push.guda.android.guda_push.R;
import push.guda.android.guda_push.conn.UdpClient;
import push.guda.android.guda_push.receiver.TickAlarmReceiver;

/**
 * Created by foodoon on 2014/12/13.
 */
public class CmdService extends Service {
    protected PendingIntent tickPendIntent;
    PowerManager.WakeLock wakeLock;
    private UdpClient udpClient;
    Notification n;
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        this.setTickAlarm();

        PowerManager pm = (PowerManager) this.getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CmdService");

        resetClient();

        notifyRunning();
    }

    @Override
    public int onStartCommand(Intent param, int flags, int startId) {
        String cmd = param.getStringExtra("CMD");
        if (cmd == null) {
            cmd = "";
        }
        if (cmd.equals("TICKET")) {
            if (wakeLock != null && wakeLock.isHeld() == false) {
                wakeLock.acquire();
            }
        }
        if (cmd.equals("RESET")) {
            if (wakeLock != null && wakeLock.isHeld() == false) {
                wakeLock.acquire();
            }
            resetClient();
        }
        if (cmd.equals("TOAST")) {
            String text = param.getStringExtra("TEXT");
            if (text != null && text.trim().length() != 0) {
                Toast.makeText(this, text, Toast.LENGTH_LONG).show();
            }
        }

        return START_STICKY;
    }

    protected void tryReleaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld() == true) {
            wakeLock.release();
        }
    }

    protected void setTickAlarm() {
        AlarmManager alarmMgr = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this, TickAlarmReceiver.class);
        int requestCode = 0;
        tickPendIntent = PendingIntent.getBroadcast(this,
                requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        long triggerAtTime = System.currentTimeMillis();
        int interval = 300 * 1000;
        alarmMgr.setRepeating(AlarmManager.RTC_WAKEUP, triggerAtTime, interval, tickPendIntent);
    }

    protected void cancelTickAlarm() {
        AlarmManager alarmMgr = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        alarmMgr.cancel(tickPendIntent);
    }

    public class UdpClientImpl extends UdpClient{
        public UdpClientImpl(final String host, final int port, long userId,int clientPort) throws Exception {

            ds = new DatagramSocket(clientPort);
            this.host = host;
            this.port = port;
            this.userId = userId;
//            / new Hearbeat();
        }
        @Override
        public void onReceiverMsg(TLV tlv) {
            if(tlv == null){
                return;
            }
            int command = CodecUtil.findTagInt(tlv, Field.CMD);
            if(command == Command.ACK){
                long seq = CodecUtil.findTagLong(tlv, Field.SEQ);
                WaitAckFactory.remove(seq);
                return;
            }
            String content = CodecUtil.findTagString(tlv, Field.CHAT_CONTENT);
            long user =  CodecUtil.findTagLong(tlv, Field.FROM_USER);
            notifyUser(1, "new msg", content, "from:[" + user+"]");
            ack(CodecUtil.newACK(tlv));
        }
    }

    private void ack(TLV tlv) {
        try {

            byte[] bytes = tlv.toBinary();
            String host = CodecUtil.findTagString(tlv, Field.TO_HOST);
            InetAddress inetAddress = InetAddress.getByName(host);

            long seq = CodecUtil.findTagLong(tlv,Field.SEQ);

            java.net.DatagramPacket sendPacket = new java.net.DatagramPacket(bytes, bytes.length,InetAddress.getByName(udpClient.getHost()) ,
                    udpClient.getPort());
            udpClient.getDs().send(sendPacket);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    protected void resetClient() {
        SharedPreferences account = this.getSharedPreferences(Params.KEY, Context.MODE_PRIVATE);
        String serverIp = account.getString(Params.SERVER_HOST, "");
        int serverPort = getInt(account.getString(Params.SERVER_PORT, ""));
        int clientPort = getInt(account.getString(Params.CLIENT_PORT, ""));
        long userId = getLong(account.getString(Params.USER_ID, ""));
        if (serverIp == null || serverIp.trim().length() == 0
                || serverPort == 0
                ||  clientPort == 0
                || userId == 0) {
            return;
        }
        if (this.udpClient != null) {
            try {
                udpClient.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        try {
            udpClient = new UdpClientImpl(serverIp,serverPort,userId,clientPort);
            Thread thread = new Thread(udpClient);
            thread.setDaemon(true);
            thread.start();
            Toast.makeText(this.getApplicationContext(), "终端重置", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this.getApplicationContext(), "操作失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }

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

    protected void notifyRunning() {
        NotificationManager notificationManager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);
        n = new Notification();
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_ONE_SHOT);
        n.contentIntent = pi;
        n.setLatestEventInfo(this, "guda-push", "正在运行", pi);
        //n.defaults = Notification.DEFAULT_ALL;
        //n.flags |= Notification.FLAG_SHOW_LIGHTS;
        //n.flags |= Notification.FLAG_AUTO_CANCEL;
        n.flags |= Notification.FLAG_ONGOING_EVENT;
        n.flags |= Notification.FLAG_NO_CLEAR;
        //n.iconLevel = 5;

        n.icon = R.drawable.ic_launcher;
        n.when = System.currentTimeMillis();
        n.tickerText = "guda-push running";
        notificationManager.notify(0, n);
    }

    protected void cancelNotifyRunning() {
        NotificationManager notificationManager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(0);
    }

    public void notifyUser(int id, String title, String content, String tickerText) {
        NotificationManager notificationManager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);
        Notification n = new Notification();
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_ONE_SHOT);
        n.contentIntent = pi;

        n.setLatestEventInfo(this, title, content, pi);
        n.defaults = Notification.DEFAULT_ALL;
        n.flags |= Notification.FLAG_SHOW_LIGHTS;
        n.flags |= Notification.FLAG_AUTO_CANCEL;

        n.icon = R.drawable.ic_launcher;
        n.when = System.currentTimeMillis();
        n.tickerText = tickerText;
        notificationManager.notify(id, n);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        //this.cancelTickAlarm();
        cancelNotifyRunning();
        this.tryReleaseWakeLock();
    }

}
