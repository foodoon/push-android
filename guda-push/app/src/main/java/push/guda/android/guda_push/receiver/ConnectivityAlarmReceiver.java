package push.guda.android.guda_push.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import push.guda.android.guda_push.service.CmdService;

/**
 * Created by foodoon on 2014/12/13.
 */
public class ConnectivityAlarmReceiver extends BroadcastReceiver {

    public ConnectivityAlarmReceiver() {
        super();
    }

    @Override
    public void onReceive(Context context, Intent intent) {

        //判断是否有网络
        Intent startSrv = new Intent(context, CmdService.class);
        startSrv.putExtra("CMD", "RESET");
        context.startService(startSrv);
    }
}
