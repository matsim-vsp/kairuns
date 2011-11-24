package playground.michalm.vrp.online;

import java.io.*;
import java.util.*;

import org.jfree.chart.*;
import org.matsim.api.core.v01.*;
import org.matsim.api.core.v01.population.*;
import org.matsim.core.api.experimental.events.*;
import org.matsim.core.config.*;
import org.matsim.core.config.groups.*;
import org.matsim.core.events.*;
import org.matsim.core.network.*;
import org.matsim.core.population.*;
import org.matsim.core.population.routes.*;
import org.matsim.core.router.*;
import org.matsim.core.router.costcalculators.*;
import org.matsim.core.router.util.*;
import org.matsim.core.scenario.*;
import org.matsim.core.trafficmonitoring.*;
import org.matsim.population.algorithms.*;
import org.matsim.ptproject.qsim.*;
import org.matsim.run.*;
import org.matsim.vis.otfvis.*;
import org.matsim.vis.otfvis.gui.*;

import pl.poznan.put.util.jfreechart.ChartUtils.OutputType;
import pl.poznan.put.vrp.dynamic.chart.*;
import pl.poznan.put.vrp.dynamic.data.*;
import pl.poznan.put.vrp.dynamic.data.model.*;
import pl.poznan.put.vrp.dynamic.optimizer.*;
import pl.poznan.put.vrp.dynamic.optimizer.taxi.*;
import pl.poznan.put.vrp.dynamic.simulator.*;
import playground.michalm.visualization.*;
import playground.michalm.visualization.VRPOTFClientLive;
import playground.michalm.vrp.data.*;
import playground.michalm.vrp.data.file.*;
import playground.michalm.vrp.data.network.*;
import playground.michalm.vrp.data.network.sparsesp.*;
import playground.michalm.vrp.demand.*;
import playground.michalm.vrp.sim.*;


public class SingleIterOnlineDVRPLauncher
{
    // schedules/routes PNG files, routes SHP files
    private static boolean VRP_OUT_FILES = true;// default: true


    public static void main(String... args)
        throws IOException
    {
        String dirName;
        String netFileName;
        String plansFileName;
        String depotsFileName;
        boolean oftVis;

        VRP_OUT_FILES = false;

        if (args.length == 1 && args[0].equals("test")) {// for testing
            dirName = "D:\\PP-rad\\taxi\\mielec\\";
            netFileName = dirName + "network.xml";
            plansFileName = dirName + "plans.xml";
            depotsFileName = dirName + "depots.xml";
            oftVis = !true;
        }
        else if (args.length == 5) {
            dirName = args[0];
            netFileName = dirName + args[1];
            plansFileName = dirName + args[2];
            depotsFileName = dirName + args[3];
            oftVis = Boolean.parseBoolean(args[4]);
        }
        else {
            throw new IllegalArgumentException("Incorrect program arguments: "
                    + Arrays.toString(args));
        }

        Config config = ConfigUtils.createConfig();

        Scenario scenario = ScenarioUtils.createScenario(config);

        new MatsimNetworkReader(scenario).readFile(netFileName);
        new MatsimPopulationReader(scenario).readFile(plansFileName);
        preparePlansForPersons(scenario);

        MATSimVRPData data = MATSimVRPDataCreator.create(scenario);
        new DepotReader(scenario, data).readFile(depotsFileName);
        createDriverPersons(scenario, data.getVrpData());

        SparseShortestPathFinder sspf = new SparseShortestPathFinder(data);
        sspf.findShortestPaths(new FreeSpeedTravelTimeCalculator(), new DijkstraFactory());
        sspf.upadateVRPArcTimesAndCosts();

        // init optimizer
        // AlgorithmParams algParams = new AlgorithmParams(new File(algParamsFileName));
        VRPOptimizerFactory optimizerFactory = new VRPOptimizerFactory() {

            @Override
            public VRPOptimizer create()
            {
                return new TaxiVRPOptimizer();
            }
        };

        // to have TravelTimeCalculatorWithBuffer instead of TravelTimeCalculator use:
        // controler.setTravelTimeCalculatorFactory(new TravelTimeCalculatorWithBufferFactory());

        final String vrpOutDirName = dirName + "\\vrp_output";
        new File(vrpOutDirName).mkdir();

        // QSim config group
        QSimConfigGroup qSimConfig = new QSimConfigGroup();
        qSimConfig.setSnapshotStyle(QSimConfigGroup.SNAPSHOT_AS_QUEUE);
        config.addQSimConfigGroup(qSimConfig);

        EventsManager events = EventsUtils.createEventsManager();
        QSim sim = createMobsim(scenario, events, data, optimizerFactory, vrpOutDirName);

        if (oftVis) { // OFTVis visualization
            OnTheFlyServer server = OTFVis.startServerAndRegisterWithQSim(scenario.getConfig(),
                    scenario, sim.getEventsManager(), sim);
            VRPOTFClientLive.run(scenario.getConfig(), server);
        }

        sim.run();

        if (VRP_OUT_FILES) {
            new Routes2QGIS(data.getVrpData().getVehicles(), data, vrpOutDirName + "\\route_")
                    .write();
        }

        // ChartUtils.showFrame(RouteChartUtils.chartRoutesByStatus(data.getVrpData()));
        // ChartUtils.showFrame(ScheduleChartUtils.chartSchedule(data.getVrpData()));
    }

    public static OTFQueryControl queryControl;

    private static QSim createMobsim(Scenario sc, EventsManager eventsManager, MATSimVRPData data,
            VRPOptimizerFactory optimizerFactory, String vrpOutDirName)
    {
        QSim sim = new QSim(sc, eventsManager);

        VRPSimEngine vrpSimEngine = new VRPSimEngine(sim, data, optimizerFactory);
        data.setVrpSimEngine(vrpSimEngine);
        sim.addMobsimEngine(vrpSimEngine);

        sim.setAgentFactory(new VRPAgentFactory(data));


        // The above is slighly confusing:
        // (1) The VRPSimEngine adds "VRP" persons to the population (in onPrepareSim) ...
        // (2) ... which are then converted into VRP agents by the agent factory.
        // One wonders if they really need to be added to the population, and if so, if this is the
        // best way to do this.
        // kai, jun'11

        // fixed the bug with creating agent before every iteration (in onPrepareSim())
        // michal, jun'11

        sim.addDepartureHandler(new TaxiModeDepartureHandler(vrpSimEngine, data));

        if (VRP_OUT_FILES) {
            vrpSimEngine.addListener(new ChartFileSimulationListener(new ChartCreator() {
                public JFreeChart createChart(VRPData data)
                {
                    return RouteChartUtils.chartRoutesByStatus(data);
                }
            }, OutputType.PNG, vrpOutDirName + "\\routes_", 800, 800));

            vrpSimEngine.addListener(new ChartFileSimulationListener(new ChartCreator() {
                public JFreeChart createChart(VRPData data)
                {
                    return ScheduleChartUtils.chartSchedule(data);
                }
            }, OutputType.PNG, vrpOutDirName + "\\schedules_", 1200, 800));
        }

        return sim;
    }


    private static void preparePlansForPersons(Scenario scenario)
    {
        Config config = scenario.getConfig();
        final NetworkImpl network = (NetworkImpl)scenario.getNetwork();

        DijkstraFactory leastCostPathCalculatorFactory = new DijkstraFactory();
        TravelTimeCalculatorFactory travelTimeCalculatorFactory = new TravelTimeCalculatorFactoryImpl();
        TravelCostCalculatorFactory travelCostCalculatorFactory = new TravelCostCalculatorFactoryImpl();
        TravelTimeCalculator travelTimeCalculator = travelTimeCalculatorFactory
                .createTravelTimeCalculator(scenario.getNetwork(), config.travelTimeCalculator());

        ModeRouteFactory routeFactory = ((PopulationFactoryImpl)scenario.getPopulation()
                .getFactory()).getModeRouteFactory();

        final PlansCalcRoute routingAlgorithm = new PlansCalcRoute(config.plansCalcRoute(),
                scenario.getNetwork(), travelCostCalculatorFactory.createTravelCostCalculator(
                        travelTimeCalculator, config.planCalcScore()), travelTimeCalculator,
                leastCostPathCalculatorFactory, routeFactory);

        routingAlgorithm.addLegHandler("taxi", new NetworkLegRouter(scenario.getNetwork(),
                routingAlgorithm.getLeastCostPathCalculator(), routingAlgorithm.getRouteFactory()));

        ParallelPersonAlgorithmRunner.run(scenario.getPopulation(), 1,
                new ParallelPersonAlgorithmRunner.PersonAlgorithmProvider() {
                    @Override
                    public AbstractPersonAlgorithm getPersonAlgorithm()
                    {
                        return new PersonPrepareForSim(routingAlgorithm, network);
                    }
                });
    }


    private static void createDriverPersons(Scenario scenario, VRPData vrpData)
    {
        Population population = scenario.getPopulation();

        for (Vehicle vrpVeh : vrpData.getVehicles()) {
            Id personId = scenario.createId(vrpVeh.getName());
            VRPDriverPerson vrpDriver = new VRPDriverPerson(personId, vrpVeh);

//            Plan dummyPlan = new PlanImpl(vrpDriver);
//            MATSimVertex vertex = (MATSimVertex)vrpVeh.getDepot().getVertex();
//            Activity dummyAct = new ActivityImpl("w", vertex.getCoord(), vertex.getLink().getId());
//            dummyPlan.addActivity(dummyAct);
//            vrpDriver.addPlan(dummyPlan);

            population.addPerson(vrpDriver);
        }
    }
}
