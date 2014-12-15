package push.guda.android.guda_push.conn;

import android.os.PowerManager;
import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

import guda.push.connect.protocol.api.Command;
import guda.push.connect.protocol.api.Field;
import guda.push.connect.protocol.api.Struct;
import guda.push.connect.protocol.codec.CodecUtil;
import guda.push.connect.protocol.codec.tlv.TLV;
import guda.push.connect.protocol.codec.tlv.TypeConvert;

/**
 * Created by foodoon on 2014/12/15.
 */
public class UdpServer implements Runnable {

    public volatile boolean started = false;
    private static PowerManager.WakeLock lock;
    InetAddress mInetAddress;
    protected byte[] buffer = new byte[1024];
    int port = 10085;
    protected static String serverHost;
    protected static int serverPort;
    protected static long userId;

    public UdpServer(PowerManager.WakeLock wakeLock,String server_host,int server_port,long user_id) {
        this.lock = wakeLock;
        this.serverPort = server_port;
        this.serverHost = server_host;
        this.userId = user_id;
    }

    public void stop(){
        started = false;
    }

    public void startListen() {

        byte[] message = new byte[100];
        try {
            DatagramSocket datagramSocket = new DatagramSocket(port);
            datagramSocket.setBroadcast(true);
            datagramSocket.setReuseAddress(true);
            DatagramPacket datagramPacket = new DatagramPacket(message,
                    message.length);
            try {
                while (started) {
                    this.lock.acquire();

                    datagramSocket.receive(datagramPacket);
                    String strMsg = new String(datagramPacket.getData()).trim();
                    Log.d("UDP Demo", datagramPacket.getAddress()
                            .getHostAddress().toString()
                            + ":" + strMsg);
                    this.lock.release();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public static void send(TLV tlv) {

        int server_port = 8904;
        Log.d("UDP Demo", "UDP发送数据:" + tlv);
        DatagramSocket s = null;
        try {
            s = new DatagramSocket();
        } catch (SocketException e) {
            e.printStackTrace();
        }
        InetAddress server = null;
        try {
            server = InetAddress.getByName(serverHost);
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        byte[] messageByte = tlv.toBinary();
        DatagramPacket p = new DatagramPacket(messageByte, messageByte.length, server,
                server_port);
        try {

            s.send(p);
            s.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        startListen();
    }

    public class Hearbeat implements Runnable{
        public Hearbeat(){
            Thread t = new Thread(this);
            t.setDaemon(true);
            t.start();
        }

        @Override
        public void run() {
            while(true) {
                try {
                    TLV tlv = CodecUtil.newTlv(Struct.HEARBEAT);
                    tlv.add(new TLV(Field.CMD, TypeConvert.int2byte(Command.HEARBEAT)));
                    tlv.add(new TLV(Field.FROM_USER, TypeConvert.long2byte(userId)));
                    tlv.add(new TLV(Field.FROM_PORT, TypeConvert.long2byte(port)));
                    send(tlv);
                }catch(Exception e){

                }
                try {
                    Thread.sleep(5 * 1000);
                } catch (Exception e) {

                }

            }
        }
    }
}
