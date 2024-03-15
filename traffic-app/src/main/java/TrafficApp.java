import org.eclipse.mosaic.fed.application.app.AbstractApplication;
import org.eclipse.mosaic.fed.application.app.api.VehicleApplication;
import org.eclipse.mosaic.fed.application.app.api.os.VehicleOperatingSystem;
import org.eclipse.mosaic.lib.objects.vehicle.VehicleData;
import org.eclipse.mosaic.lib.util.scheduling.Event;
  
public class TrafficApp extends AbstractApplication<VehicleOperatingSystem> implements VehicleApplication {
       
    @Override
    public void onStartup() {
        getLog().info("Hello World!");
    }
  
    @Override
    public void onVehicleUpdated(VehicleData previousVehicleData, VehicleData updatedVehicleData) {
        getLog().info("Driving {} m/s.", updatedVehicleData.getSpeed());
    }
   
    @Override
    public void onShutdown() {
        getLog().info("Good bye!");
    }
   
    @Override
    public void processEvent(Event event) {
        // ...
    }
}
