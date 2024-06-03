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
import org.eclipse.mosaic.lib.objects.road.IRoadPosition;
import org.eclipse.mosaic.lib.objects.v2x.MessageRouting;
import org.eclipse.mosaic.lib.util.scheduling.Event;
import org.eclipse.mosaic.rti.TIME;

public final class VehicleToTrafficLightApp extends AbstractApplication<VehicleOperatingSystem> implements CommunicationApplication {
    private final static long TIME_INTERVAL = TIME.SECOND / 10;
    private final static String JUNCTION_ID = "42429874";
    private final static String TL_ID = "rsu_0";
    private final static Integer MAX_DISTANCE_RANGE = 30;
    private final static Integer MAX_RSU_DISTANCE = 50;
    private final static long MAX_MESSAGE_WAIT = TIME.SECOND * 10;

    
    private final static GeoPoint RSU_GEO_POINT = new MutableGeoPoint(40.743457, -73.988281);

    private final static GeoPoint ROUTE123_STOP_POINT_0 = new MutableGeoPoint(40.743503, -73.988432);
    private final static GeoPoint ROUTE123_STOP_POINT_1 = new MutableGeoPoint(40.743523, -73.988407);
    
    private final static GeoPoint ROUTE456_STOP_POINT_0 = new MutableGeoPoint(40.74357498932027, -73.98821495898788);
    private final static GeoPoint ROUTE456_STOP_POINT_1 = new MutableGeoPoint(40.743556191906855, -73.98817472580086);
    private final static GeoPoint ROUTE456_STOP_POINT_2 = new MutableGeoPoint(40.74354095075524, -73.98814388037415);
    private final static GeoPoint ROUTE456_STOP_POINT_3 = new MutableGeoPoint(40.74352672565851, -73.98810699997745);
    
    private final static GeoPoint ROAD_1_GEO_POINT_S = new MutableGeoPoint(40.743484, -73.988400);
    private final static GeoPoint ROAD_1_GEO_POINT_E = new MutableGeoPoint(40.743626, -73.988620);
    private final static BrakingArea ROAD_1_AREA = new BrakingArea(ROAD_1_GEO_POINT_S, ROAD_1_GEO_POINT_E);
    
    private final static GeoPoint ROAD_2_GEO_POINT_S = new MutableGeoPoint(40.743540, -73.988297);
    private final static GeoPoint ROAD_2_GEO_POINT_E = new MutableGeoPoint(40.743618, -73.988081);
    private final static BrakingArea ROAD_2_AREA = new BrakingArea(ROAD_2_GEO_POINT_S, ROAD_2_GEO_POINT_E);

    public Map<String, CAM> vehicles = new HashMap<String, CAM>();
    public boolean run = false;
    public Queue<Pair<GreenWaveMsg,Long>> message_queue = new LinkedList<Pair<GreenWaveMsg,Long>>();

    public ItsBritneyBitch buildBitch() {
        ItsBritneyBitch britney = new ItsBritneyBitch();
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
        while (queue_size > 0){
            Pair<GreenWaveMsg,Long> queue = message_queue.poll();
            if (getOs().getSimulationTime() - queue.getRight() < MAX_MESSAGE_WAIT){
                if (queue.getLeft().getMessage().payload instanceof ItsBritneyBitch){
                    forward_bitch_queue(queue);
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
            getLog().infoSimTime(this, "No vehicles in range. Nor the RSU.");
            return;
        }

        ItsBritneyBitch message_data = buildBitch();
        getOs().getAdHocModule().sendV2xMessage(new GreenWaveMsg(routing,new RawPayload(message_data, TL_ID)));
        getLog().infoSimTime(this, "Sent message to " + IpResolver.getSingleton().reverseLookup(routing.getDestination().getAddress().address));
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

    private void forward_bitch_queue(Pair<GreenWaveMsg,Long> queue){
        var routing = buildRouting_toRSU();
        if (routing == null){
            message_queue.offer(queue);
            return;
        }
        forward_bitch(queue.getLeft());
    }

    private void forward_bitch(GreenWaveMsg receivedMessage) {
        var routing = buildRouting_toRSU();
        if (routing == null){
            getLog().infoSimTime(this, "No vehicles in range. Nor the RSU.");
            Pair<GreenWaveMsg,Long> queue = Pair.of(receivedMessage,getOs().getSimulationTime());

            message_queue.offer(queue);
            return;
        }
        ItsBritneyBitch message_Bitch = (ItsBritneyBitch) receivedMessage.getMessage().payload;
        if (message_Bitch.TTL <= 0){
            // This message is no longer usefull
            return;
        } 
        message_Bitch.TTL = message_Bitch.TTL-1;
        getOs().getAdHocModule().sendV2xMessage(new GreenWaveMsg(routing,new RawPayload(message_Bitch, TL_ID)));
        getLog().infoSimTime(this, "Forwarded Britney message to " + IpResolver.getSingleton().reverseLookup(routing.getDestination().getAddress().address));
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
                    break;
                case "SLOW_DOWN":
                    getOs().slowDown(10, 1000);
                    break;
                case "GO":
                    getOs().resume();
                    break;
                default:
                    break;
            }
    
            if (stopPoint != null) {
                getLog().infoSimTime(this, "Attempting to stop at: " + stopPoint);
                var stoping_point = navModule.getClosestRoadPosition(stopPoint);
                getLog().infoSimTime(this, "Closest road position: " + stoping_point);
                getOs().stop(stoping_point, VehicleStopMode.STOP, 100);
            } else {
                getLog().infoSimTime(this, "No valid stop point found for the current route and lane.");
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
                getLog().infoSimTime(this, "Traffic light state: " + traffic_light.state.toString());
    
                // Log the stop point coordinates
                GeoPoint stopPoint = null;
    
                if (traffic_light.state.containsKey(my_route)) {
                    var route_status = traffic_light.state.get(my_route).get(my_lane);
                    getLog().infoSimTime(this, "Route status for lane: " + route_status);
    
                    switch (route_status.toString()) {
                        case "RED":
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
                                getLog().infoSimTime(this, "Stopping at: " + stopPoint);
                                var stopping_point = navModule.getClosestRoadPosition(stopPoint);
                                getLog().infoSimTime(this, "Closest road position: " + stopping_point);
                                getOs().stop(stopping_point, VehicleStopMode.STOP, 100);
                            } else {
                                getLog().infoSimTime(this, "No valid stop point found for the current route and lane.");
                            }
                            break;
    
                        case "GREEN":
                            // Keep moving
                            getOs().resume();
                            break;
    
                        case "YELLOW":
                            // Slow down
                            getOs().slowDown(10, 1000);
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

        // Send Britney Message one every two iterations
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
                //System.out.println(getOs().getId() + " - Received CAM message from " + cam.id + " at " + cam.position.toString());
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
            TL traffic_light = (TL) receivedMessage.getMessage().payload;
            // Change state of vehicle based on the state of the traffic light
            managed_by_traffic_light(traffic_light);
            // Check status and act accordingly
            //System.out.println( getOs().getId() + " - Received TL message from " + traffic_light.id);

        }
        if (raw.payload instanceof ItsBritneyBitch) {
            if (receivedV2xMessage.getMessage().getRouting().getSource().getSourcePosition()
            .distanceTo(getOs().getPosition()) > MAX_DISTANCE_RANGE){
                return;
            }
            // Forward to another next vehicle or TL
            forward_bitch(receivedMessage);

            //var message = (ItsBritneyBitch) receivedMessage.getMessage().payload;
            //System.out.println( getOs().getId() + " - Received Britney message from " + message.id + " to " + receivedMessage.getMessage().destination );

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
