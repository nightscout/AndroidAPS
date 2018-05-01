package com.gxwtech.roundtrip2.RoundtripService.Tasks;

import com.gxwtech.roundtrip2.RoundtripService.RoundtripService;
import com.gxwtech.roundtrip2.RoundtripService.medtronic.PumpData.Page;
import com.gxwtech.roundtrip2.ServiceData.FetchPumpHistoryResult;
import com.gxwtech.roundtrip2.ServiceData.RetrieveHistoryPageResult;
import com.gxwtech.roundtrip2.ServiceData.ServiceTransport;

import java.net.MalformedURLException;
import java.util.ArrayList;

/**
 * Created by geoff on 7/16/16.
 */
public class FetchPumpHistoryTask extends PumpTask {
    public FetchPumpHistoryTask() { }
    public FetchPumpHistoryTask(ServiceTransport transport) {
        super(transport);
    }
    private FetchPumpHistoryResult result = new FetchPumpHistoryResult();

    @Override
    public void run() {
        ArrayList<Page> ra = new ArrayList<>();
        for (int i=0; i<16; i++) {
            Page page = RoundtripService.getInstance().pumpManager.getPumpHistoryPage(i);
            if (page != null) {
                ra.add(page);
                RoundtripService.getInstance().saveHistoryPage(i,page);
            }
        }

        result.setMap(getServiceTransport().getServiceResult().getMap());
        result.setResultOK();
        result.setPageArray(ra);
        getServiceTransport().setServiceResult(result);
    }


}
