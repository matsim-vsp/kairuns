package playground.lu.congestionAwareDrt;

import org.matsim.api.core.v01.network.Network;
import org.matsim.contrib.drt.optimizer.DrtOptimizer;
import org.matsim.contrib.drt.optimizer.QSimScopeForkJoinPoolHolder;
import org.matsim.contrib.drt.optimizer.VehicleData;
import org.matsim.contrib.drt.optimizer.insertion.UnplannedRequestInserter;
import org.matsim.contrib.drt.optimizer.rebalancing.RebalancingStrategy;
import org.matsim.contrib.drt.run.DrtConfigGroup;
import org.matsim.contrib.drt.scheduler.DrtScheduleInquiry;
import org.matsim.contrib.drt.scheduler.EmptyVehicleRelocator;
import org.matsim.contrib.dvrp.fleet.Fleet;
import org.matsim.contrib.dvrp.run.AbstractDvrpModeQSimModule;
import org.matsim.contrib.dvrp.schedule.ScheduleTimingUpdater;
import org.matsim.contrib.dvrp.trafficmonitoring.DvrpTravelTimeModule;
import org.matsim.core.mobsim.framework.MobsimTimer;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;

public class CongestionAwareDrtModule extends AbstractDvrpModeQSimModule {

	private final DrtConfigGroup drtCfg;

	public CongestionAwareDrtModule(DrtConfigGroup drtCfg) {
		super(drtCfg.getMode());
		this.drtCfg = drtCfg;
	}

	@Override
	protected void configureQSim() {
		// Binding the Drt Optimizer to Congestion Aware Optimizer
		bindModal(DrtOptimizer.class)
				.toProvider(modalProvider(getter -> new CongestionAwareDrtOptimizer(drtCfg,
						getter.getModal(Fleet.class), getter.get(MobsimTimer.class),
						getter.getModal(RebalancingStrategy.class), getter.getModal(DrtScheduleInquiry.class),
						getter.getModal(ScheduleTimingUpdater.class), getter.getModal(EmptyVehicleRelocator.class),
						getter.getModal(UnplannedRequestInserter.class), getter.getModal(ReroutingStrategy.class))))
				.asEagerSingleton();

		// Instruction for creating Vehicle re-routing Tool
		bindModal(ReroutingStrategy.class).toProvider(modalProvider(
				getter -> new ReroutingStrategy(getter.getNamed(TravelTime.class, DvrpTravelTimeModule.DVRP_ESTIMATED),
						drtCfg, getter.getModal(Network.class), getter.getModal(TravelDisutility.class),
						getter.getModal(VehicleData.EntryFactory.class),
						getter.getModal(QSimScopeForkJoinPoolHolder.class).getPool())))
				.asEagerSingleton();

		// Instruction for creating Congestion averting travel disutility
		// And then add event handler binding to the disutility
		bindModal(CongestionAvertingTravelDisutility.class)
				.toProvider(modalProvider(getter -> new CongestionAvertingTravelDisutility())).asEagerSingleton();
		addMobsimScopeEventHandlerBinding().to(modalKey(CongestionAvertingTravelDisutility.class));
		
		// binding the travel disutility to congestion averting travel disutility
		bindModal(TravelDisutility.class).to(modalKey(CongestionAvertingTravelDisutility.class));
	}
}
