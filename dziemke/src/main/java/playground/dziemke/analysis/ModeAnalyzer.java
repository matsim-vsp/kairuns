package playground.dziemke.analysis;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.*;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.core.utils.io.UncheckedIOException;
import playground.dziemke.analysis.modalShare.ModalShareDiagramCreator;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.*;

/**
 * @author dziemke
 */
public class ModeAnalyzer {
    public static final Logger LOG = Logger.getLogger(ModalShareDiagramCreator.class);

    public static void main(String[] args) {
        // String directory = "../../runs-svn/open_berlin_scenario/v5.3-policies/output/n2-01/";
        // String runId = "berlin-v5.3-10pct-ctd-n2-01";
        // String plansFile = directory + runId + ".experiencedPlans_withResidence.xml.gz";
        // String configFile = directory + runId + ".output_config_adjusted.xml";

        String directory = "../../runs-svn/open_berlin_scenario/v5.5-bicycle/bc-122/output/";
        String runId = "berlin-v5.5-1pct-122";
        // String plansFile = directory + "berlin-v5.5-1pct-15.output_plans.xml.gz";
        // String plansFile = directory + "berlin-v5.5-1pct-15.experiencedPlans_withResidence_inside.xml.gz";
        String configFile = directory + runId + ".output_config.xml";
        String plansFile = directory + runId + ".output_plans_no-freight_berlin.xml.gz";
        String modeFileName = directory + "modes_berlin.txt";
//        String plansFile = directory + runId + ".output_plans_no-freight.xml.gz";
//        String modeFileName = directory + "modes.txt";

        if (args.length == 3) {
            configFile = args[0];
            plansFile = args[1];
            modeFileName = args[2];
        }

        Map<String,Double> modeCnt = new TreeMap<>() ;
        Set<String> modes;
        BufferedWriter modeOut = IOUtils.getBufferedWriter(modeFileName);
        Map<String,Map<Integer,Double>> modeHistories = new HashMap<>() ;

        Config config = ConfigUtils.loadConfig(configFile);
        Scenario scenario = ScenarioUtils.createScenario(config);
        new PopulationReader(scenario).readFile(plansFile);
        Population population = scenario.getPopulation();

        try {
            modeOut.write("Iteration");
            modes = new TreeSet<>();
            modes.addAll(config.planCalcScore().getAllModes());
            LOG.info("Modes included: " + modes);
            for (String mode : modes) {
                modeOut.write("\t" + mode);
            }
            modeOut.write("\n"); ;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        for (Person person : population.getPersons().values()) {
            Plan plan = person.getSelectedPlan();
            List<TripStructureUtils.Trip> trips = TripStructureUtils.getTrips(plan);
            for (TripStructureUtils.Trip trip : trips) {
                Leg firstLegOfTrip = (Leg) trip.getTripElements().get(0);
                String mode;

                // If routingMode is available
                mode = (String) firstLegOfTrip.getAttributes().getAttribute("routingMode");

                // If not, use old main mode detection
                if (mode == null) {
                    mode = identifyMainMode(trip.getTripElements());
                }

                Double cnt = modeCnt.get(mode);
                if ( cnt==null ) {
                    cnt = 0. ;
                }
                modeCnt.put( mode, cnt + 1 ) ;
            }
        }

        double sum = 0 ;
        for ( Double val : modeCnt.values() ) {
            sum += val ;
        }

        try {
            modeOut.write(1) ;
            for (String mode : modes) {
                Double cnt = modeCnt.get(mode) ;
                double share = 0. ;
                if (cnt!=null) {
                    share = cnt/sum;
                }
                LOG.info("Mode share of " + mode + " = " + share);
                modeOut.write("\t" + share);

                Map<Integer, Double> modeHistory = modeHistories.get(mode) ;
                if ( modeHistory == null ) {
                    modeHistory = new TreeMap<>() ;
                    modeHistories.put(mode, modeHistory) ;
                }
                modeHistory.put( 1, share ) ;
            }
            modeOut.write("\n");
            modeOut.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String identifyMainMode(final List<? extends PlanElement> tripElements) {
        String mode = ((Leg) tripElements.get(0)).getMode();
        if (mode.equals(TransportMode.transit_walk)) {
            LOG.info("mode: " + TransportMode.pt);
            return TransportMode.pt;
        }
        for (PlanElement pe : tripElements) {
            if (pe instanceof Leg) {
                Leg leg = (Leg) pe;
                String mode2 = leg.getMode() ;
                if (!mode2.contains(TransportMode.non_network_walk) &&
                        !mode2.contains(TransportMode.transit_walk) &&
                        !mode2.contains("access_walk") &&
                        !mode2.contains("egress_walk")) {
                    return mode2 ;
                }
            }
        }
        throw new RuntimeException("Could not identify main mode "+ tripElements);
    }
}