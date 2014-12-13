package push.guda.android.guda_push.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;


import push.guda.android.guda_push.service.CmdService;
import push.guda.android.guda_push.util.NetUtil;


public class TickAlarmReceiver extends BroadcastReceiver {


    public TickAlarmReceiver() {

    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if(!NetUtil.connected()){
            return;
        }
        Intent startSrv = new Intent(context, CmdService.class);
        startSrv.putExtra("CMD", "TICKET");
        context.startService(startSrv);
    }

}
