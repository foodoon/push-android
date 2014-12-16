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
import push.guda.android.guda_push.listener.ReceiveMsgListener;

/**
 * Created by foodoon on 2014/12/15.
 */
public class UdpServer implements Runnable {

    public volatile boolean started = true;
    private static PowerManager.WakeLock lock;
    InetAddress mInetAddress;
    protected byte[] buffer = new byte[1024];
    static int port = 10085;
    protected static String serverHost;
    protected static int serverPort;
    protected static long userId;
    private Hearbeat hearbeat;

    private ReceiveMsgListener receiveMsgListener;

    public UdpServer(PowerManager.WakeLock wakeLock,String server_host,int server_port,long user_id,int clientPort) {
        this.lock = wakeLock;
        this.serverPort = server_port;
        this.serverHost = server_host;
        this.userId = user_id;
        this.port = clientPort;
        hearbeat = new Hearbeat();
    }

    public void stop(){
        started = false;
        if(hearbeat!=null){
            hearbeat.stop();
        }
    }

    public void startListen() {

        byte[] message = new byte[100];
        try {
            DatagramSocket datagramSocket = null;
            while(datagramSocket == null){
                try {
                    datagramSocket = new DatagramSocket(port);
                }catch(Exception e){
                    port++ ;
                   Thread.sleep(300);
                }
            }
            datagramSocket.setBroadcast(true);
            datagramSocket.setReuseAddress(true);
            DatagramPacket datagramPacket = new DatagramPacket(message,
                    message.length);
            try {
                while (started) {
                    this.lock.acquire();

                    datagramSocket.receive(datagramPacket);
                    TLV rece = new TLV(datagramPacket.getData());
                    Log.d("UDP Demo", datagramPacket.getAddress()
                            .getHostAddress().toString()
                            + ":" + rece);
                    if(receiveMsgListener!=null){
                        receiveMsgListener.onReceive(rece);
                    }
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
        if(tlv == null){
            return;
        }
        TLV tag = CodecUtil.findTag(tlv, Field.FROM_PORT);
        if(tag == null){
            tlv.add(new TLV(Field.FROM_PORT,port));
        }else{
            tag.setValue(TypeConvert.int2byte(port));
        }
        Log.d("UDP", "UDP发送数据:" + tlv);

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
                serverPort);
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
        public volatile boolean started = true;
        public Hearbeat(){
            Thread t = new Thread(this);
            t.setDaemon(true);
            t.start();
        }

        public void stop(){
            started = false;
        }

        @Override
        public void run() {
            while(started) {
                try {
                    TLV tlv = CodecUtil.newTlv(Struct.HEARBEAT);
                    tlv.add(new TLV(Field.CMD, TypeConvert.int2byte(Command.HEARBEAT)));
                    tlv.add(new TLV(Field.FROM_USER, TypeConvert.long2byte(userId)));
                    tlv.add(new TLV(Field.FROM_PORT, TypeConvert.long2byte(port)));
                    send(tlv);
                }catch(Exception e){

                }
                try {
                    Thread.sleep(60 * 1000 * 3);
                } catch (Exception e) {

                }

            }
        }
    }

    public void setReceiveMsgListener(ReceiveMsgListener receiveMsgListener) {
        this.receiveMsgListener = receiveMsgListener;
    }
}
