/*
 * Copyright (c) 2020 Fraunhofer FOKUS and others. All rights reserved.
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contact: mosaic@fokus.fraunhofer.de
 */

package org.eclipse.mosaic.app.tutorial;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.mosaic.app.tutorial.message.GreenWaveMsg;
import org.eclipse.mosaic.app.tutorial.message.RawPayload;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.AdHocModuleConfiguration;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.CamBuilder;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.ReceivedAcknowledgement;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.ReceivedV2xMessage;
import org.eclipse.mosaic.fed.application.app.AbstractApplication;
import org.eclipse.mosaic.fed.application.app.api.CommunicationApplication;
import org.eclipse.mosaic.fed.application.app.api.os.VehicleOperatingSystem;
import org.eclipse.mosaic.interactions.communication.V2xMessageTransmission;
import org.eclipse.mosaic.lib.enums.AdHocChannel;
import org.eclipse.mosaic.lib.enums.VehicleStopMode;
import org.eclipse.mosaic.lib.geo.GeoPoint;
import org.eclipse.mosaic.lib.geo.MutableGeoPoint;
import org.eclipse.mosaic.lib.objects.addressing.AdHocMessageRoutingBuilder;
import org.eclipse.mosaic.lib.objects.addressing.IpResolver;
import org.eclipse.mosaic.lib.objects.v2x.MessageRouting;
import org.eclipse.mosaic.lib.util.scheduling.Event;
import org.eclipse.mosaic.rti.TIME;

public final class VehicleToTrafficLightApp extends AbstractApplication<VehicleOperatingSystem> implements CommunicationApplication {
    private final static long TIME_INTERVAL = TIME.SECOND / 10;
    private final static String JUNCTION_ID = "42429874";
    private final static String TL_ID = "rsu_0";
    private final static Integer MAX_DISTANCE_RANGE = 50;
    private final static Integer MAX_RSU_DISTANCE = 50;
    private final static long MAX_MESSAGE_WAIT = TIME.SECOND * 10;
    private final static long STOP_TIME = 5 * TIME.SECOND;

    
    private final static GeoPoint RSU_GEO_POINT = new MutableGeoPoint(40.743457, -73.988281);

    private final static GeoPoint ROUTE123_STOP_POINT_0 = new MutableGeoPoint(40.743499, -73.988424);
    private final static GeoPoint ROUTE123_STOP_POINT_1 = new MutableGeoPoint(40.743524, -73.988404);
    
    private final static GeoPoint ROUTE456_STOP_POINT_0 = new MutableGeoPoint(40.743550, -73.988275);
    private final static GeoPoint ROUTE456_STOP_POINT_1 = new MutableGeoPoint(40.743535, -73.988240);
    private final static GeoPoint ROUTE456_STOP_POINT_2 = new MutableGeoPoint(40.743521, -73.988210);
    private final static GeoPoint ROUTE456_STOP_POINT_3 = new MutableGeoPoint(40.743507, -73.988176);
    
    private final static GeoPoint ROAD_1_GEO_POINT_S = new MutableGeoPoint(40.743480, -73.988414);
    private final static GeoPoint ROAD_1_GEO_POINT_E = new MutableGeoPoint(40.743710, -73.988811);
    private final static BrakingArea ROAD_1_AREA = new BrakingArea(ROAD_1_GEO_POINT_S, ROAD_1_GEO_POINT_E);
    
    private final static GeoPoint ROAD_2_GEO_POINT_S = new MutableGeoPoint(40.743543, -73.988297);
    private final static GeoPoint ROAD_2_GEO_POINT_E = new MutableGeoPoint(40.743725, -73.987997);
    private final static BrakingArea ROAD_2_AREA = new BrakingArea(ROAD_2_GEO_POINT_S, ROAD_2_GEO_POINT_E);

    public Map<String, CAM> vehicles = new HashMap<String, CAM>();
    public boolean run = false;
    public boolean recievedStop = false;
    public Queue<Pair<GreenWaveMsg,Long>> message_queue = new LinkedList<Pair<GreenWaveMsg,Long>>();

    public InDetectionZone buildDetection() {
        InDetectionZone britney = new InDetectionZone();
        britney.id = getOs().getId();
        britney.position = getOs().getPosition();
        var NavModule = getOs().getNavigationModule();
        if (NavModule != null){
            var currRoute = NavModule.getCurrentRoute();
            var vehData = NavModule.getVehicleData();
            if (vehData != null) {
                var roadPos = vehData.getRoadPosition();
                if (roadPos != null) {
                    britney.lane = roadPos.getLaneIndex();
                }
                else {
                    britney.lane = 0;
                }
            }
            if (currRoute != null) {
                britney.route = Integer.parseInt(currRoute.getId());
            }
            else {
                britney.route = 0;
            }
        }
        return britney;
    }

    public CAM buildCAM(){
        CAM cam = new CAM();
        
        var NavModule = getOs().getNavigationModule();
        if (NavModule != null) {
            var vehData = NavModule.getVehicleData();
            if (vehData != null) {
                var roadPos = vehData.getRoadPosition();
                var currRoute = NavModule.getCurrentRoute();
                cam.id = getOs().getId();
                cam.speed = vehData.getSpeed();
                cam.direction = vehData.getDriveDirection();
                var junction = NavModule.getNode(JUNCTION_ID);
                if (junction != null) {
                    cam.isMovingTowards = junction.getId().equals(JUNCTION_ID);
                }
                else {
                    //System.out.println("Junction is null");
                }
                cam.acceleration = vehData.getThrottle();
                if (roadPos != null) {
                    cam.lane = roadPos.getLaneIndex();                   
                }
                else {
                    cam.lane = 0;
                }
                if (currRoute != null) {
                    cam.route = currRoute.getId();
                }
                else {
                    cam.route = "";
                }
                cam.position = getOs().getPosition();
            }
        }
        return cam;
    }

    private MessageRouting buildRouting_toRSU() {
        final AdHocMessageRoutingBuilder routing_builder = getOperatingSystem()
        .getAdHocModule()
        .createMessageRouting();

        var distances = new ArrayList<Pair<CAM, Double>>();
        var my_distance = getOs().getPosition().distanceTo(RSU_GEO_POINT);
        if (my_distance > MAX_DISTANCE_RANGE) {
            getLog().infoSimTime(this, "Vehicle is too far away from the traffic light.");
            //System.out.println("Distance: " + my_distance + " Max Distance: " + MAX_DISTANCE_RANGE);
            if (vehicles.isEmpty()) {
                getLog().infoSimTime(this, "No vehicles in range.");
                return null;
            }
            for (CAM vehicle : this.vehicles.values()){
                Pair<CAM, Double> distance = Pair.of(vehicle, vehicle.position.distanceTo(RSU_GEO_POINT));
                distances.add(distance);
            }
            Pair<CAM, Double> closest_vehicle = distances.get(0);
            for (var pair_distance: distances) {
                if (pair_distance.getValue() < closest_vehicle.getValue()) {
                    closest_vehicle = pair_distance;
                }
            }
            // send message to vehicle closest to the TL
            if (closest_vehicle.getValue() < my_distance)
                return routing_builder.topoCast(closest_vehicle.getKey().id, 1);
            else
                return null;
        }
        else {
            // send message to traffic light
            return routing_builder.topoCast(TL_ID, 1);
        }
    }

    private MessageRouting buildRouting_toDestination(String destination_id) {
        final AdHocMessageRoutingBuilder routing_builder = getOperatingSystem()
        .getAdHocModule()
        .createMessageRouting();

        if (vehicles.isEmpty()){
            getLog().infoSimTime(this, "No vehicles in range.");
            return null;
        }

        // Check if the destination exists in the vehicles map
        if (vehicles.containsKey(destination_id)) {
            return routing_builder.topoCast(destination_id, 1);
        }
        else {
            // Send broadcast message to all nearby vehicles ??? Probably not the best approach because STORM
            // FIXME: Send message to the closest vehicle to the destination
            return routing_builder.topoBroadCast();
        }
    }

    private void processQueue(){
        if (getOs().getPosition().distanceTo(RSU_GEO_POINT) > MAX_DISTANCE_RANGE && vehicles.isEmpty()){
            return;
        }
        int queue_size = message_queue.size();
        getLog().infoSimTime(this, "Atempting to process queue");
        while (queue_size > 0){
            Pair<GreenWaveMsg,Long> queue = message_queue.poll();
            if (getOs().getSimulationTime() - queue.getRight() < MAX_MESSAGE_WAIT){
                if (queue.getLeft().getMessage().payload instanceof InDetectionZone){
                    forward_detection_queue(queue);
                }
                if (queue.getLeft().getMessage().payload instanceof Control){
                    forward_control_queue(queue);
                }
            }
            queue_size = queue_size - 1;
        }
    }


    private void sendCAM(){
        MessageRouting routing = getOperatingSystem().getAdHocModule().createMessageRouting().topoBroadCast();
        CAM cam = buildCAM();

        getOs().getAdHocModule().sendV2xMessage(new GreenWaveMsg(routing,new RawPayload(cam, "veh_x")));
        getLog().infoSimTime(this, "Sent broadcast CAM message to all vehicles");
    }

    public void send_britney_info(){
        // check if TL is in range, if not check if any car is in range but closer to the TL, if not return, else send message, else send message to TL
        MessageRouting routing = buildRouting_toRSU();

        if (routing == null) {
            getLog().infoSimTime(this, "Neither the RSU nor any vehicle is in range.");
            return;
        }

        InDetectionZone message_data = buildDetection();
        getOs().getAdHocModule().sendV2xMessage(new GreenWaveMsg(routing,new RawPayload(message_data, TL_ID)));
        getLog().infoSimTime(this, "Sent Detection message to " + IpResolver.getSingleton().reverseLookup(routing.getDestination().getAddress().address));
    } 

    private void sendTopoMessage() {
        if (run) { // Run once every two iterations

            if (ROAD_1_AREA.isPointInsideSquare(getOs().getPosition()) || ROAD_2_AREA.isPointInsideSquare(getOs().getPosition())) {
                getLog().infoSimTime(this, "Vehicle is inside the detection area.");
                send_britney_info();
            }
            else {
                getLog().infoSimTime(this, "Vehicle is outside the detection area.");
            }
            run = false;
        }
        else
            run = true;
    }

    private void forward_detection_queue(Pair<GreenWaveMsg,Long> queue){
        var routing = buildRouting_toRSU();
        if (routing == null){
            message_queue.offer(queue);
            return;
        }
        forward_detection(queue.getLeft());
    }

    private void forward_detection(GreenWaveMsg receivedMessage) {
        var routing = buildRouting_toRSU();
        if (routing == null){
            getLog().infoSimTime(this, "No vehicles in range. Nor the RSU.");
            Pair<GreenWaveMsg,Long> queue = Pair.of(receivedMessage,getOs().getSimulationTime());
            message_queue.offer(queue);
            return;
        }
        InDetectionZone message_Bitch = (InDetectionZone) receivedMessage.getMessage().payload;
        if (message_Bitch.TTL <= 0){
            // This message is no longer usefull
            return;
        } 
        message_Bitch.TTL = message_Bitch.TTL-1;
        getOs().getAdHocModule().sendV2xMessage(new GreenWaveMsg(routing,new RawPayload(message_Bitch, TL_ID)));
        getLog().infoSimTime(this, "Forwarded Detection message to " + IpResolver.getSingleton().reverseLookup(routing.getDestination().getAddress().address));
    }

    private void forward_control_queue(Pair<GreenWaveMsg,Long> queue){
        var routing = buildRouting_toDestination(queue.getLeft().getMessage().destination);
        if (routing == null){
            message_queue.offer(queue);
            return;
        }
        forward_control(queue.getLeft());
    }

    private void forward_control(GreenWaveMsg receivedMessage){

        // question: how do i get the position of the destination vehicle?
        // answer: 
        
        var routing = buildRouting_toDestination(receivedMessage.getMessage().destination);
        if (routing == null){
            getLog().infoSimTime(this, "No vehicles in range.");
            Pair<GreenWaveMsg,Long> queue = Pair.of(receivedMessage,getOs().getSimulationTime());
            message_queue.offer(queue);
            return;
        }

        Control control_message = (Control) receivedMessage.getMessage().payload;
        if (control_message.TTL <= 0){
            // Drop this message
            return;
        }
        control_message.TTL = control_message.TTL-1;
        getOs().getAdHocModule().sendV2xMessage(new GreenWaveMsg(routing,new RawPayload(control_message, receivedMessage.getMessage().destination)));
        getLog().infoSimTime(this, "Forwarded Control message to " + IpResolver.getSingleton().reverseLookup(routing.getDestination().getAddress().address));

    }

    private void follow_command(Control control) {
        var navModule = getOs().getNavigationModule();
        if (navModule != null) {
            var currRoute = navModule.getCurrentRoute();
            var roadPos = navModule.getVehicleData().getRoadPosition();
            var my_lane = String.valueOf(roadPos.getLaneIndex());
            var my_route = currRoute.getId();
            
            // Log current route and lane information
            getLog().infoSimTime(this, "Current route: " + my_route + ", lane: " + my_lane);
            getLog().infoSimTime(this, "Vehicle position: " + getOs().getPosition());
            
            // Log the stop point coordinates
            GeoPoint stopPoint = null;
            switch (control.rule.toString()) {
                case "STOP":
                case "RED":
                    if (recievedStop){
                        return;
                    } else {
                        if (my_route.equals("1") || my_route.equals("2") || my_route.equals("3")) {
                            if (my_lane.equals("0")) {
                                stopPoint = ROUTE123_STOP_POINT_0;
                            } else if (my_lane.equals("1")) {
                                stopPoint = ROUTE123_STOP_POINT_1;
                            }
                        } else if (my_route.equals("4") || my_route.equals("5") || my_route.equals("6")) {
                            switch (my_lane) {
                                case "0": stopPoint = ROUTE456_STOP_POINT_0; break;
                                case "1": stopPoint = ROUTE456_STOP_POINT_1; break;
                                case "2": stopPoint = ROUTE456_STOP_POINT_2; break;
                                case "3": stopPoint = ROUTE456_STOP_POINT_3; break;
                            }
                        }
                        if (stopPoint != null) {
                            var cam = buildCAM();
                            var stoping_point = stopPoint.distanceTo(getOs().getPosition());
                            stoping_point = stoping_point - 10.0;
                            getLog().infoSimTime(this, "Attempting to stop in " + stoping_point + " meters.");
                            if (stoping_point <= 0.0 && cam.isMovingTowards){
                                getLog().infoSimTime(this, "Vehicle is stopping now.");
                                getOs().stopNow(VehicleStopMode.STOP, STOP_TIME);
                                recievedStop = true;
                            }
                            else {
                                getLog().infoSimTime(this, "Vehicle is not moving towards the stop point or the stop point is too far away.");
                            }
                        } else {
                            getLog().infoSimTime(this, "No valid stop point found for the current route and lane.");
                        }
                    }
                    break;
                case "SLOW_DOWN":
                case "YELLOW":
                    getOs().slowDown(10, STOP_TIME);
                    break;
                case "GO":
                case "GREEN":
                    if (!recievedStop){
                        return;
                    } else {
                        getOs().resume();
                        getLog().infoSimTime(this, "Car is moving.");
                        recievedStop = false;    
                    }
                    break;
                default:
                    break;
            }
        }
    }

    public void managed_by_traffic_light(TL traffic_light) {
        var navModule = getOs().getNavigationModule();
        if (!traffic_light.isOff) {
            if (navModule != null) {
                var currRoute = navModule.getCurrentRoute();
                var roadPos = navModule.getVehicleData().getRoadPosition();
                var my_lane = String.valueOf(roadPos.getLaneIndex());
                var my_route = currRoute.getId();
    
                // Log current route, lane, and traffic light state
                getLog().infoSimTime(this, "Current route: " + my_route + ", lane: " + my_lane);
                getLog().infoSimTime(this, "Vehicle position: " + getOs().getPosition());
    
                // Log the stop point coordinates
                GeoPoint stopPoint = null;
    
                if (traffic_light.state.containsKey(my_route)) {
                    var route_status = traffic_light.state.get(my_route).get(my_lane);
                    getLog().infoSimTime(this, "Route status for lane: " + route_status);
    
                    switch (route_status.toString()) {
                        case "RED":
                            if (recievedStop){
                                return;
                            } else {
                                if (my_route.equals("1") || my_route.equals("2") || my_route.equals("3")) {
                                    if (my_lane.equals("0")) {
                                        stopPoint = ROUTE123_STOP_POINT_0;
                                    } else if (my_lane.equals("1")) {
                                        stopPoint = ROUTE123_STOP_POINT_1;
                                    }
                                } else if (my_route.equals("4") || my_route.equals("5") || my_route.equals("6")) {
                                    switch (my_lane) {
                                        case "0": stopPoint = ROUTE456_STOP_POINT_0; break;
                                        case "1": stopPoint = ROUTE456_STOP_POINT_1; break;
                                        case "2": stopPoint = ROUTE456_STOP_POINT_2; break;
                                        case "3": stopPoint = ROUTE456_STOP_POINT_3; break;
                                    }
                                }
        
                                if (stopPoint != null) {
                                    var cam = buildCAM();
                                    var stoping_point = stopPoint.distanceTo(getOs().getPosition());
                                    stoping_point = stoping_point - 10.0;
                                    getLog().infoSimTime(this, "Attempting to stop in " + stoping_point + " meters.");
                                    if (stoping_point <= 0.0 && cam.isMovingTowards){
                                        getLog().infoSimTime(this, "Vehicle is stopping now.");
                                        getOs().stopNow(VehicleStopMode.STOP, STOP_TIME);
                                        recievedStop = true;
                                    }
                                } else {
                                    getLog().infoSimTime(this, "No valid stop point found for the current route and lane.");
                                }
                            }                           
                            break;
    
                        case "GREEN":
                            if (!recievedStop){
                                return;
                            } else {
                                // Keep moving
                                getOs().resume();
                                getLog().infoSimTime(this, "Car is moving.");
                                recievedStop = false;
                            }
                            break;
    
                        case "YELLOW":
                            // Slow down
                            getOs().slowDown(1, STOP_TIME);
                            getLog().infoSimTime(this, "Attempting to slow down to 1 m/s.");
                            break;
    
                        default:
                            break;
                    }
                } else {
                    getLog().infoSimTime(this, "No route status available for the current route.");
                }
            }
        } else {
            // Traffic light is off, proceed with caution
            getLog().infoSimTime(this, "Traffic light is off, proceeding with caution.");
            getOs().slowDown(10, 1000);
        }
    }
    

    private void sample() {
        getOs().getEventManager().addEvent(
                getOs().getSimulationTime() + TIME_INTERVAL, this
        );
        // Clean Queue
        processQueue();

        // Send Detection Message one every two iterations
        sendTopoMessage();

        // Send CAM Message
        sendCAM();
    }

    @Override
    public void onStartup() {
        getLog().infoSimTime(this, "Initialize application");
        AdHocModuleConfiguration configuration = new AdHocModuleConfiguration()
                .addRadio()
                .channel(AdHocChannel.CCH)
                .power(17)
                .distance(300)
                .create();
        getOs().getAdHocModule().enable(configuration);
        getLog().infoSimTime(this, "Activated WLAN Module");
        sample();
    }

    @Override
    public void onShutdown() {
        getLog().infoSimTime(this, "Shutdown application");
    }

    @Override
    public void processEvent(Event event) throws Exception {
        sample();
    }

    @Override
    public void onMessageReceived(ReceivedV2xMessage receivedV2xMessage) {
        if (!(receivedV2xMessage.getMessage() instanceof GreenWaveMsg)) {
            return;
        }
        GreenWaveMsg receivedMessage = (GreenWaveMsg) receivedV2xMessage.getMessage();
        var raw = receivedMessage.getMessage();
        String send_to = receivedMessage.getMessage().destination;

        // Populate the vehicles map with the near vehicles in range
        if (raw.payload instanceof CAM) {
            CAM cam = (CAM) receivedMessage.getMessage().payload;
            if (cam.position.distanceTo(getOs().getPosition()) <= MAX_DISTANCE_RANGE) {
                getLog().infoSimTime(this, "Received CAM message from " + cam.id);
                vehicles.put(cam.id, cam);
            }
            else {
                vehicles.remove(cam.id);
            }
            
        }
        if (raw.payload instanceof Control) {
            if (receivedV2xMessage.getMessage().getRouting().getSource().getSourcePosition()
            .distanceTo(getOs().getPosition()) > MAX_RSU_DISTANCE){
                return;
            }
            getLog().infoSimTime(this, "Received Control message from " + IpResolver.getSingleton().reverseLookup(receivedMessage.getRouting().getDestination().getAddress().address));
            // Check if the message is for this vehicle
                // If yes OBEY
                // If not send to another vehicle behind
            if (send_to.equals(getOs().getId())) {
                Control control = (Control) receivedMessage.getMessage().payload;
                // Follow the control message
                follow_command(control);
            }
            else {
                // Send control message to the vehicle closest to the destination
                forward_control(receivedMessage);
            }
        }
        if (raw.payload instanceof TL) {
            if (receivedV2xMessage.getMessage().getRouting().getSource().getSourcePosition()
            .distanceTo(getOs().getPosition()) > MAX_RSU_DISTANCE){
                return;
            }
            getLog().infoSimTime(this, "Received TL_Status message from " + IpResolver.getSingleton().reverseLookup(receivedMessage.getRouting().getSource().getSourceAddress().address));
            TL traffic_light = (TL) receivedMessage.getMessage().payload;
            // Change state of vehicle based on the state of the traffic light
            managed_by_traffic_light(traffic_light);
            // Check status and act accordingly
            //System.out.println( getOs().getId() + " - Received TL message from " + traffic_light.id);

        }
        if (raw.payload instanceof InDetectionZone) {
            if (receivedV2xMessage.getMessage().getRouting().getSource().getSourcePosition()
            .distanceTo(getOs().getPosition()) > MAX_DISTANCE_RANGE){
                return;
            }
            getLog().infoSimTime(this, "Received Detection message from " + IpResolver.getSingleton().reverseLookup(receivedMessage.getRouting().getDestination().getAddress().address) + " to " + receivedMessage.getMessage().destination);
            // Forward to another next vehicle or TL
            forward_detection(receivedMessage);
        }
    }

    @Override
    public void onAcknowledgementReceived(ReceivedAcknowledgement acknowledgedMessage) {
        // nop
    }

    @Override
    public void onCamBuilding(CamBuilder camBuilder) {
        // nop
    }

    @Override
    public void onMessageTransmitted(V2xMessageTransmission v2xMessageTransmission) {
        // nop
    }

}
