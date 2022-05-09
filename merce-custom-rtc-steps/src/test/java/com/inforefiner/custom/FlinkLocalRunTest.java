package com.inforefiner.custom;


import com.merce.woven.dataflow.flink.FlinkJobCli;
import org.junit.Test;

import java.net.URL;

/**
 * Created by DebugSy on 2019/5/22.
 */
public class FlinkLocalRunTest {

    private String flow = "/flows/debug/debug.json";
    private String conf = "/flows/flow-conf.json";

    @Test
    public void testRun() throws Exception {

//        HDFSTestEnv.init();

        URL flowUrl = this.getClass().getResource(flow);
        String flowPath = flowUrl.getPath();

        URL confUrl = this.getClass().getResource(conf);
        String confPath = confUrl.getPath();

        String[] args = new String[]{"--mode", "local", "--name", "flink-test", "--type", "1", "--job", flowPath,
                "--conf", confPath, "--callback-rpc-url", "", "--monitor-jobId-id", "00000000000000000000000000000001"};

        FlinkJobCli cli = new FlinkJobCli();
        cli.run(args);

    }

}
