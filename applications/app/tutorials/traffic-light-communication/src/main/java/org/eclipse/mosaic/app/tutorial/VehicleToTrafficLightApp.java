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
import org.eclipse.mosaic.lib.objects.v2x.MessageRouting;
import org.eclipse.mosaic.lib.util.scheduling.Event;
import org.eclipse.mosaic.rti.TIME;

public final class VehicleToTrafficLightApp extends AbstractApplication<VehicleOperatingSystem> implements CommunicationApplication {
    private final static long TIME_INTERVAL = TIME.SECOND / 4;
    private final static String TRAFFIC_LIGHT_ID = "42429874";
    private final static String TL_ID = "tl_0";
    private final static Integer MAX_DISTANCE_RANGE = 15;
    private final static long MAX_MESSAGE_WAIT = TIME.SECOND * 100;

    private final static int CAM_HASH = new CAM().hashCode();
    private final static int CONTROL_HASH = new Control().hashCode();
    private final static int TL_HASH = new TL().hashCode();
    private final static int ITS_BRITNEY_HASH = new ItsBritneyBitch().hashCode();
    
    private final static GeoPoint RSU_GEO_POINT = new MutableGeoPoint(40.743456325244175, -73.98820410000964);

    private final static GeoPoint ROUTE123_STOP_POINT_0 = new MutableGeoPoint(40.74352012132382, -73.98837723293325);
    private final static GeoPoint ROUTE123_STOP_POINT_1 = new MutableGeoPoint(40.743553143747434, -73.98835644581);

    private final static GeoPoint ROUTE456_STOP_POINT_0 = new MutableGeoPoint(40.74357498932027, -73.98821495898788);
    private final static GeoPoint ROUTE456_STOP_POINT_1 = new MutableGeoPoint(40.743556191906855, -73.98817472580086);
    private final static GeoPoint ROUTE456_STOP_POINT_2 = new MutableGeoPoint(40.74354095075524, -73.98814388037415);
    private final static GeoPoint ROUTE456_STOP_POINT_3 = new MutableGeoPoint(40.74352672565851, -73.98810699997745);
    private final static GeoPoint ROUTE456_STOP_POINT_4 = new MutableGeoPoint(40.74351148448957, -73.98807682512994);
    
    private final static GeoPoint ROAD_1_GEO_POINT_S = new MutableGeoPoint(40.74347018352965, -73.98833612944004);
    private final static GeoPoint ROAD_1_GEO_POINT_E = new MutableGeoPoint(40.74383698515376, -73.98897986094623);
    private final static BrakingArea ROAD_1_AREA = new BrakingArea(ROAD_1_GEO_POINT_S, ROAD_1_GEO_POINT_E);
    
    private final static GeoPoint ROAD_2_GEO_POINT_S = new MutableGeoPoint(40.74353876044327, -73.98826219097081);
    private final static GeoPoint ROAD_2_GEO_POINT_E = new MutableGeoPoint(40.74367386310809, -73.98793752269356);
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
                cam.isMovingTowards = NavModule.getNextTrafficLightNode().getId().equals(TRAFFIC_LIGHT_ID);;
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
            return routing_builder.topoCast(closest_vehicle.getKey().id, 1);
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
            // Send broadcast message to all nearby vehicles ??? Probably not the best approach
            // FIXME: Send message to the closest vehicle to the destination
            return routing_builder.topoBroadCast();
        }
    }

    private void processQueue(){
        if (getOs().getPosition().distanceTo(RSU_GEO_POINT) > MAX_DISTANCE_RANGE && vehicles.isEmpty()){
            return;
        }
        while (!message_queue.isEmpty()){
            Pair<GreenWaveMsg,Long> queue = message_queue.poll();
            if (getOs().getSimulationTime() - queue.getRight() < MAX_MESSAGE_WAIT){
                if (queue.getLeft().getMessage().payload_type == ITS_BRITNEY_HASH){
                    forward_bitch_queue(queue);
                }
                if (queue.getLeft().getMessage().payload_type == CONTROL_HASH){
                    forward_control_queue(queue);
                }
            }
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
        getLog().infoSimTime(this, "Sent message to " + routing.getDestination().toString());
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
        getLog().infoSimTime(this, "Forwarded Britney message to " + routing.getDestination().toString());
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
        getLog().infoSimTime(this, "Forwarded Control message to " + routing.getDestination().toString());

    }

    private void follow_command(Control control) {
        var navModule = getOs().getNavigationModule();
        if (navModule != null) {
            var currRoute = navModule.getCurrentRoute();
            var my_lane = String.valueOf(navModule.getVehicleData().getRoadPosition().getLaneIndex());
            var my_route = currRoute.getId();
            switch (control.rule.toString()) {
                case "STOP":
                int rota_n = Integer.parseInt(my_route);
                if (rota_n < 4) {
                    if (my_lane.equals("0")) {
                        var stoping_point =  getOs().getNavigationModule().getClosestRoadPosition(ROUTE123_STOP_POINT_0);
                        getOs().stop(stoping_point, VehicleStopMode.STOP, 100);
                    }
                    else if (my_lane.equals("1")) {
                        var stoping_point =  getOs().getNavigationModule().getClosestRoadPosition(ROUTE123_STOP_POINT_1);
                        getOs().stop(stoping_point, VehicleStopMode.STOP, 100);                                
                    }
                    else {
                        // Vehicle is not on the route
                    }
                }
                else {
                    if (my_lane.equals("0")) {
                        var stoping_point =  getOs().getNavigationModule().getClosestRoadPosition(ROUTE456_STOP_POINT_0);
                        getOs().stop(stoping_point, VehicleStopMode.STOP, 100);
                    }
                    else if (my_lane.equals("1")) {
                        var stoping_point =  getOs().getNavigationModule().getClosestRoadPosition(ROUTE456_STOP_POINT_1);
                        getOs().stop(stoping_point, VehicleStopMode.STOP, 100);                                
                    }
                    else if (my_lane.equals("2")) {
                        var stoping_point =  getOs().getNavigationModule().getClosestRoadPosition(ROUTE456_STOP_POINT_2);
                        getOs().stop(stoping_point, VehicleStopMode.STOP, 100);                                
                    }
                    else if (my_lane.equals("3")) {
                        var stoping_point =  getOs().getNavigationModule().getClosestRoadPosition(ROUTE456_STOP_POINT_3);
                        getOs().stop(stoping_point, VehicleStopMode.STOP, 100);                                
                    }
                    else if (my_lane.equals("4")) {
                        var stoping_point =  getOs().getNavigationModule().getClosestRoadPosition(ROUTE456_STOP_POINT_4);
                        getOs().stop(stoping_point, VehicleStopMode.STOP, 100);                                
                    }
                    else {
                        // Vehicle is not on the route
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
        }
    }

    public void managed_by_traffic_light(TL traffic_light) {
        var navModule = getOs().getNavigationModule();
        if (!traffic_light.isOff) {
            if (navModule != null) {
                var currRoute = navModule.getCurrentRoute();
                var my_lane = String.valueOf(navModule.getVehicleData().getRoadPosition().getLaneIndex());
                var my_route = currRoute.getId();
                var route_status = traffic_light.state.get(my_route).get(my_lane);
                switch (route_status.toString()) {
                    case "RED":
                        int rota_n = Integer.parseInt(my_route);
                        if (rota_n < 4) {
                            if (my_lane.equals("0")) {
                                var stoping_point =  getOs().getNavigationModule().getClosestRoadPosition(ROUTE123_STOP_POINT_0);
                                getOs().stop(stoping_point, VehicleStopMode.STOP, 100);
                            }
                            else if (my_lane.equals("1")) {
                                var stoping_point =  getOs().getNavigationModule().getClosestRoadPosition(ROUTE123_STOP_POINT_1);
                                getOs().stop(stoping_point, VehicleStopMode.STOP, 100);                                
                            }
                            else {
                                // Vehicle is not on the route
                            }
                        }
                        else {
                            if (my_lane.equals("0")) {
                                var stoping_point =  getOs().getNavigationModule().getClosestRoadPosition(ROUTE456_STOP_POINT_0);
                                getOs().stop(stoping_point, VehicleStopMode.STOP, 100);
                            }
                            else if (my_lane.equals("1")) {
                                var stoping_point =  getOs().getNavigationModule().getClosestRoadPosition(ROUTE456_STOP_POINT_1);
                                getOs().stop(stoping_point, VehicleStopMode.STOP, 100);                                
                            }
                            else if (my_lane.equals("2")) {
                                var stoping_point =  getOs().getNavigationModule().getClosestRoadPosition(ROUTE456_STOP_POINT_2);
                                getOs().stop(stoping_point, VehicleStopMode.STOP, 100);                                
                            }
                            else if (my_lane.equals("3")) {
                                var stoping_point =  getOs().getNavigationModule().getClosestRoadPosition(ROUTE456_STOP_POINT_3);
                                getOs().stop(stoping_point, VehicleStopMode.STOP, 100);                                
                            }
                            else if (my_lane.equals("4")) {
                                var stoping_point =  getOs().getNavigationModule().getClosestRoadPosition(ROUTE456_STOP_POINT_4);
                                getOs().stop(stoping_point, VehicleStopMode.STOP, 100);                                
                            }
                            else {
                                // Vehicle is not on the route
                            }
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
                

            }
        }
        else
        {
            // Traffic light is off procede with caution
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
        String send_to = receivedMessage.getMessage().destination;
        int payload_type = receivedMessage.getMessage().payload_type;

        // Populate the vehicles map with the near vehicles in range
        if (payload_type == CAM_HASH) {
            CAM cam = (CAM) receivedMessage.getMessage().payload;
            if (cam.position.distanceTo(getOs().getPosition()) <= MAX_DISTANCE_RANGE) {
                vehicles.put(cam.id, cam);
            }
            else {
                vehicles.remove(cam.id);
            }
        }
        if (payload_type == CONTROL_HASH) {
            if (receivedV2xMessage.getMessage().getRouting().getSource().getSourcePosition()
            .distanceTo(getOs().getPosition()) > MAX_DISTANCE_RANGE){
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
        if (payload_type == TL_HASH) {
            if (receivedV2xMessage.getMessage().getRouting().getSource().getSourcePosition()
            .distanceTo(getOs().getPosition()) > MAX_DISTANCE_RANGE){
                return;
            }
            TL traffic_light = (TL) receivedMessage.getMessage().payload;
            // Change state of vehicle based on the state of the traffic light
            managed_by_traffic_light(traffic_light);
            // Check status and act accordingly

        }
        if (payload_type == ITS_BRITNEY_HASH) {
            if (receivedV2xMessage.getMessage().getRouting().getSource().getSourcePosition()
            .distanceTo(getOs().getPosition()) > MAX_DISTANCE_RANGE){
                return;
            }
            // Forward to another next vehicle or TL
            forward_bitch(receivedMessage);
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
