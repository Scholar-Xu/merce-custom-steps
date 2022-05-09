package com.inforefiner.custom.udf;

import com.merce.woven.annotation.UDF;
import org.apache.flink.table.functions.ScalarFunction;

@UDF(name = "rtc_udf_dis")
public class MapUtils extends ScalarFunction {

    private static final long serialVersionUID = 969728366213789895L;
    private static final double EARTH_RADIUS = 6378137.0D;

    private static double rad(double d)
    {
        return d * 3.141592653589793D / 180.0D;
    }

    public static double GetDistance(double lon1, double lat1, double lon2, double lat2)
    {
        double radLat1 = rad(lat1);
        double radLat2 = rad(lat2);
        double a = radLat1 - radLat2;
        double b = rad(lon1) - rad(lon2);
        double s = 2.0D * Math.asin(Math.sqrt(Math.pow(Math.sin(a / 2.0D), 2.0D) + Math.cos(radLat1) * Math.cos(radLat2) *
                Math.pow(Math.sin(b / 2.0D), 2.0D)));
        s *= 6378137.0D;

        return s;
    }

    public double eval(Double paramT1, Double paramT2, Double paramT3, Double paramT4) {
        return Double.valueOf(GetDistance(paramT1.doubleValue(), paramT2.doubleValue(), paramT3.doubleValue(), paramT4.doubleValue()));
    }

}
