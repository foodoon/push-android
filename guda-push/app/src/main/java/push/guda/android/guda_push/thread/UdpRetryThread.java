package push.guda.android.guda_push.thread;

import guda.push.connect.queue.AckTLV;
import guda.push.connect.queue.WaitAckFactory;
import guda.push.connect.protocol.api.Field;
import guda.push.connect.protocol.codec.CodecUtil;
import guda.push.connect.protocol.codec.tlv.TLV;
import guda.push.connect.protocol.codec.tlv.TypeConvert;
import guda.push.connect.udp.host.HostInfo;
import guda.push.connect.queue.OnlineInfo;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

/**
 * Created by foodoon on 2014/12/12.
 */
public class UdpRetryThread implements Runnable{

    private volatile  boolean started = true;
    public UdpRetryThread(){
        Thread t =new Thread(this);
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
                AckTLV ackTLV = WaitAckFactory.poll();
                if (ackTLV == null) {
                    try {
                        Thread.sleep(1 * 1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                if(!ackTLV.needRetry()){
                    long seq = CodecUtil.findTagLong(ackTLV.getTlv(),Field.SEQ);
                    WaitAckFactory.add(seq,ackTLV.getTlv());
                    return;
                }
                TLV tlv = ackTLV.getTlv();
                //reset online info
                long toUser = CodecUtil.findTagLong(tlv, Field.TO_USER);
                HostInfo onlineInfo = OnlineInfo.findOnlineInfo(toUser);
                if (onlineInfo == null) {
                    return;
                }
                TLV tagPort = CodecUtil.findTag(tlv, Field.TO_PORT);
                TLV tagHost = CodecUtil.findTag(tlv, Field.TO_HOST);
                tagHost.setValue(TypeConvert.string2byte(onlineInfo.getHost()));
                tagPort.setValue(TypeConvert.int2byte(onlineInfo.getPort()));
                //
                DatagramSocket sendSocket = new DatagramSocket();
                byte[] bytes = tlv.toBinary();

                InetAddress inetAddress = InetAddress.getByName(onlineInfo.getHost());
                DatagramPacket sendPacket = new DatagramPacket(bytes, bytes.length, inetAddress,
                        onlineInfo.getPort());
                sendSocket.send(sendPacket);
                sendSocket.close();
                long seq = CodecUtil.findTagLong(tlv, Field.SEQ);
                WaitAckFactory.add(seq, tlv);
            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

        }
    }
}
