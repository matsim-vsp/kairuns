//package playground.dziemke.accessibility.OTPMatrix;
//
//import org.locationtech.jts.geom.Coordinate;
//
//import org.apache.log4j.Logger;
//import org.junit.Assert;
//import org.junit.Rule;
//import org.junit.Test;
//import org.matsim.contrib.accessibility.AccessibilityModule;
//import org.matsim.testcases.MatsimTestUtils;
//import org.opentripplanner.routing.graph.Graph;
//
//import java.io.File;
//import java.text.ParseException;
//import java.text.SimpleDateFormat;
//import java.util.Calendar;
//import java.util.TimeZone;
//
//public class OTPTimeRouterTest {
//
//    @Rule
//    public MatsimTestUtils utils = new MatsimTestUtils();
//
//   	private static final Logger LOG = Logger.getLogger(OTPTimeRouterTest.class);
//
//    @Test
//    public void testMatrixRouting() throws Exception {
//
//        final Calendar calendar = Calendar.getInstance();
//        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
//        TimeZone timeZone = TimeZone.getTimeZone("America/Nevada");
//        df.setTimeZone(timeZone);
//        calendar.setTimeZone(timeZone);
//        try {
//            calendar.setTime(df.parse("2009-09-09"));
//        } catch (ParseException e) {
//            e.printStackTrace();
//        }
//        calendar.add(Calendar.SECOND, 53880);
//
//        String input_dir = utils.getInputDirectory();
//        OTPMatrixRouter.buildGraph(input_dir);
//        Graph graph = OTPMatrixRouter.loadGraph(input_dir);
//
//        Coordinate origin = new Coordinate(36.914893,-116.76821);
//        Coordinate destination = new Coordinate(36.905697,-116.76218);
//        long result = OTPMatrixRouter.getSingleRouteTime(graph, calendar, origin, destination);
//
//        //delete the graphfile
//        File graphFile = new File(input_dir + "Graph.obj");
//        graphFile.delete();
//
//        Assert.assertEquals(727, result);
//
//        LOG.info("Shutdown");
//    }
//}
