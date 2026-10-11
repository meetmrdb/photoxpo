package spike;

import android.content.pm.ServiceInfo;

/** PROTOTYPE: same as CopyService but declared dataSync, the fallback type. */
public class CopyServiceDataSync extends CopyService {
    @Override int type() { return ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC; }
}
