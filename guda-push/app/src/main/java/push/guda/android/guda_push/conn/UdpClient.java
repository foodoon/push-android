package push.guda.android.guda_push.conn;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;


import guda.push.connect.protocol.api.Command;
import guda.push.connect.protocol.api.Field;
import guda.push.connect.protocol.api.Struct;
import guda.push.connect.protocol.codec.CodecUtil;
import guda.push.connect.protocol.codec.tlv.TLV;
import guda.push.connect.protocol.codec.tlv.TypeConvert;

/**
 * Created by foodoon on 2014/12/13.
 */
public abstract class UdpClient implements Runnable {

    protected byte[] buffer = new byte[1024];

    protected DatagramSocket ds = null;

    protected String host;
    protected int port;
    protected long userId;
    protected int clientPort;

    private volatile  boolean started = true;

    public void stop(){
        started = false;
    }

    public DatagramSocket getDs() {
        return ds;
    }

    public void setDs(DatagramSocket ds) {
        this.ds = ds;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    public int getClientPort() {
        return clientPort;
    }

    public void setClientPort(int clientPort) {
        this.clientPort = clientPort;
    }

    //    public UdpClient(final String host, final int port, long userId) throws Exception {
//        ds = new DatagramSocket();
//        this.host = host;
//        this.port = port;
//        this.userId = userId;
//    }


    public final void setSoTimeout(final int timeout) throws Exception {
        ds.setSoTimeout(timeout);
    }


    public final int getSoTimeout() throws Exception {
        return ds.getSoTimeout();
    }

    public final DatagramSocket getSocket() {
        return ds;
    }


    public final DatagramPacket send(final byte[] bytes) throws IOException {
        DatagramPacket dp = new DatagramPacket(bytes, bytes.length, InetAddress
                .getByName(host), port);
        ds.send(dp);
        return dp;
    }


    public final TLV receive()
            throws Exception {
        if(ds!=null &&ds.isBound()&& ds.isConnected()){


        DatagramPacket dp = new DatagramPacket(buffer, buffer.length);
        ds.receive(dp);
        byte[] d = new byte[dp.getLength()];
        System.arraycopy(dp.getData(), 0, d, 0, dp.getLength());

        TLV info = new TLV(d);
        info.add(new TLV(Field.FROM_HOST,TypeConvert.string2byte(dp.getAddress().getHostAddress())));
        info.add(new TLV(Field.FROM_PORT,TypeConvert.int2byte(dp.getPort())));
        System.out.println("rece:"+info);
        return info;
        }
        return null;
    }


    public final void close() {
        try {
            ds.close();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public byte[] newMsg(long fromUserId, long targetUserId, String content) {
        TLV tlv = CodecUtil.newTlv(Struct.CHAT);
        tlv.add(new TLV(Field.CMD, TypeConvert.int2byte(Command.CHAT)));
        tlv.add(new TLV(Field.CHAT_CONTENT, TypeConvert.string2byte(content + " [from:" + fromUserId + "]")));
        tlv.add(new TLV(Field.FROM_USER, TypeConvert.long2byte(fromUserId)));
        tlv.add(new TLV(Field.TO_USER, TypeConvert.long2byte(targetUserId)));
        return tlv.toBinary();
    }




     public abstract void onReceiverMsg(TLV tlv);

    @Override
    public void run() {
        while (started) {
            try {

                    TLV info = receive();
                if(info!=null) {
                    onReceiverMsg(info);
                }

            } catch (Exception e) {
                  e.printStackTrace();

            }
            try {
                Thread.sleep(1 * 1000);
            } catch (Exception e) {

            }
        }

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
                    tlv.add(new TLV(Field.FROM_PORT, TypeConvert.long2byte(clientPort)));
                    send(tlv.toBinary());
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
