package push.guda.android.guda_push.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;

import push.guda.android.guda_push.service.CmdService;

/**
 * Created by foodoon on 2014/12/13.
 */
public class NoticeReceiver extends BroadcastReceiver {

    PowerManager.WakeLock wakeLock;

    public NoticeReceiver() {

    }

    @Override
    public void onReceive(Context context, Intent intent) {

        Intent startSrv = new Intent(context, CmdService.class);
        startSrv.putExtra("CMD", "TICKET");
        context.startService(startSrv);
    }
}
