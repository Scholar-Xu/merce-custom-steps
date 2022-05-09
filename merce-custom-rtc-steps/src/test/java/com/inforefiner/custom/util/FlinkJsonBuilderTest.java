package com.inforefiner.custom.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.wnameless.json.flattener.JsonFlattener;
import junit.framework.TestCase;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class FlinkJsonBuilderTest extends TestCase {

    private FlinkJsonBuilder flinkJsonBuilder = FlinkJsonBuilder.getInstance();

    ObjectMapper MAPPER = new ObjectMapper();

    public void testToJson() {
        Map<String, Object> map = new HashMap<>();
        map.put("1", "A");
        map.put("2", null);
        map.put("3", 0);
        String toJson = flinkJsonBuilder.toJson(map);
        System.out.println(toJson);
    }

    public void testParseJson() throws IOException {
        String json = "{\"table\":\"TESTOGG.ACCT_LOAN\",\"op_type\":\"I\",\"op_ts\":\"2021-05-07 18:06:000046\",\"current_ts\":\"2021-05-07T18:06:46.000199\",\"pos\":\"000000005500000084l3\",\"primary_keys\":[\"SERIALNO\"],\"after\":{\"SERIALNO\":\"ae13db6e67d94b50a2f2b8b307df4bf6\",\"ACCOUNTNO\":null,\"CONTRACTSERIALNO\":null,\"CUSTOMERID\":\"test0507_v3_8465280\",\"CUSTOMERNAME\":\"test0507_kafka_v3_8465280\",\"BUSINESSTYPE\":null,\"PRODUCTID\":null,\"SPECIFICSERIALNO\":null,\"VERSIONID\":null,\"CURRENCY\":null,\"BUSINESSSUM\":null,\"PUTOUTDATE\":null,\"MATURITYDATE\":null,\"ORIGINALMATURITYDATE\":null,\"OPERATEORGID\":null,\"ACCOUNTINGORGID\":null,\"LOANSTATUS\":null,\"FINISHDATE\":null,\"BUSINESSDATE\":null,\"LOCKFLAG\":null,\"OVERDUEDAYS\":null,\"CLASSIFYRESULT\":null,\"PUTOUTSERIALNO\":null,\"APPROVESERIALNO\":null,\"APPLYSERIALNO\":null,\"BUSINESSSTATUS\":\"0\",\"MAXOVERDUEDAYS\":null,\"NORMALBALANCE\":0,\"OVERDUEBALANCE\":0,\"ACCRUEDINTEREST\":0,\"OVERDUEINTEREST\":0,\"PRINCIPALPENALTY\":0,\"INTERESTPENALTY\":0,\"OVERDUEFEE\":0,\"LASTDAYNORMALBALANCE\":0,\"LASTDAYOVERDUEBALANCE\":0,\"LASTDAYACCRUEDINTEREST\":0,\"LASTDAYOVERDUEINTEREST\":0,\"LASTDAYPRINCIPALPENALTY\":0,\"LASTDAYINTERESTPENALTY\":0,\"LASTDAYOVERDUEFEE\":0,\"DRATE\":null,\"TOTALTERMS\":null,\"CURRTERM\":null,\"UNCLEARTERMS\":null,\"GRANCEPERIOD\":null,\"CORPUSPAYMETHOD\":null,\"TRUSTPROJECTCODE\":null,\"CANCELDATE\":null,\"CANCELPRINCIPAL\":null,\"CANCELINTERESTPENLATY\":null,\"OVERDUEDATE\":null,\"LOANUSE\":null,\"USEAREA\":null,\"RATETYPE\":null,\"DAYRATE\":null,\"MONTHRATE \":null,\"PRINREPAY_FREQUENCY \":null,\"INTREPAY_FREQUENCY \":null,\"VOUCHTYPE \":null,\"CREDITLINEID \":null,\"ENCASHACCTTYPE \":null,\"ENCASHACCTNO \":null,\"REPAYACCTTYPE \":null,\"REPAYACCTNO \":null,\"APPLYNO \":null,\"NEXT_REPAY_DATE \":null,\"OVD_TERMS \":null,\"PRIN_OVD_DAYS \":null,\"INT_OVD_DAYS \":null,\"YEARRATE \":null,\"INTERESTRATE \":null,\"COMINTERESTRATE \":null,\"DEFULTMONTHS \":null,\"PUTOUTSUM \":null,\"UNPAYBALANCE \":null,\"TRUSTPROJECTNAME \":null,\"CARD_BANKNAME \":null,\"UNPRINCIPAL \":null,\"UNINTEREST \":null,\"ACCULOAN \":null,\"DUEDAY \":null,\"FPAYDATE \":null}}";
        String message = JsonFlattener.flatten(json);
        MAPPER.readValue(message, Map.class);
    }
}