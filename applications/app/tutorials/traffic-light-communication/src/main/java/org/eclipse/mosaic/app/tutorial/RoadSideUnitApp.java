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

 import java.util.Map;
 import java.util.HashMap;
 import java.util.Set;
 import java.util.HashSet;
 
 import org.eclipse.mosaic.app.tutorial.Control.Rule;
 import org.eclipse.mosaic.app.tutorial.message.GreenWaveMsg;
 import org.eclipse.mosaic.app.tutorial.message.RawPayload;
 import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.AdHocModuleConfiguration;
 import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.CamBuilder;
 import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.ReceivedAcknowledgement;
 import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.ReceivedV2xMessage;
 import org.eclipse.mosaic.fed.application.app.AbstractApplication;
 import org.eclipse.mosaic.fed.application.app.api.CommunicationApplication;
 import org.eclipse.mosaic.fed.application.app.api.os.RoadSideUnitOperatingSystem;
 import org.eclipse.mosaic.interactions.communication.V2xMessageTransmission;
 import org.eclipse.mosaic.lib.enums.AdHocChannel;
 import org.eclipse.mosaic.lib.objects.v2x.MessageRouting;
 import org.eclipse.mosaic.lib.util.scheduling.Event;
 import org.eclipse.mosaic.rti.TIME;
 
 /**
  * Road Side Unit Application used for MOSAIC Tiergarten Tutorial.
  * Sends inter-application messages via broadcast in order to show
  * how to differentiate between intra vehicle and inter vehicle application messages.
  */
 public class RoadSideUnitApp extends AbstractApplication<RoadSideUnitOperatingSystem> implements CommunicationApplication {
     /**
      * Interval at which messages are sent (every 0.5 seconds).
      */
     private final static long TIME_INTERVAL = TIME.SECOND / 10;
     private final static String RSU_ID = "rsu_0";
     private final static Integer MAX_DISTANCE_RANGE = 30;
     public final static long TRAFFIC_TIMER = 10 * TIME.SECOND;
     public final static int LANE_THRESHOLD = 5;
 
 
     public Map<String,CAM> car_table = new HashMap<String,CAM>();
     public Map<String,InDetectionZone> car_information = new HashMap<String,InDetectionZone>();
     public boolean is_off = false;
     public Map<String,Map<String,Set<String>>> lane_queue = new HashMap<String,Map<String,Set<String>>>();
     public TrafficLight traffic_light = new TrafficLight();
     public long last_state_change = 0;
 
     public void sample() {
         getOs().getEventManager().addEvent(
                 getOs().getSimulationTime() + TIME_INTERVAL, this
         );
         manage_traffic();
         //sendState();
     }
 
     public TL buildState(){
         TL state = new TL();
         state.id = getOs().getId();
         state.state = traffic_light.get_state();
         state.isOff = is_off;
         return state;
     }
 
     public void sendState() {
         MessageRouting routing = getOperatingSystem().getAdHocModule().createMessageRouting().topoBroadCast();
         TL state = buildState();
 
         getOs().getAdHocModule().sendV2xMessage(new GreenWaveMsg(routing,new RawPayload(state, "veh_x")));
         getLog().infoSimTime(this, "Sent broadcast State message to all vehicles");
     }
 
     public Control build_control(String route, String lane, String destination) {
         Control control = new Control();

         if (car_table.containsKey(destination)){
             control.general_location = car_table.get(destination).position;
         }
         else if (car_information.containsKey(destination)){
             control.general_location = car_information.get(destination).position;
         }
         
         control.to_who = destination;
         Rule rule = Rule.GO;
         switch (traffic_light.trafficLightStatus.get(route).get(lane).toString()) {
             case "RED":
                 rule = Rule.STOP;
                 break;
 
             case "GREEN":
                 rule = Rule.GO;
                 break;
 
             case "YELLOW":
                 rule = Rule.SLOW_DOWN;
                 break;
         
             default:
                 rule = Rule.SLOW_DOWN;
                 break;
         }
         control.rule = rule;
         return control;
     }
 
     public MessageRouting routingTo_car(String car_id, String route, String lane) {
         if (car_table.isEmpty())
             return null;
         else{
             if (car_table.containsKey(car_id)){
                 return getOperatingSystem().getAdHocModule().createMessageRouting().topoCast(car_id, 1);
             } else {
                 String best_car = car_table.keySet().iterator().next();
                 for (Map.Entry<String, CAM> car : car_table.entrySet()) {
                     if (car.getValue().route.equals(route) && car.getValue().lane == Integer.parseInt(lane)){
                         if (car.getValue().position.distanceTo(getOs().getPosition()) < car_table.get(best_car).position.distanceTo(getOs().getPosition())) {
                             best_car = car.getKey();
                         }
                     }
                 }
                 return getOperatingSystem().getAdHocModule().createMessageRouting().topoCast(best_car, 1);
             }
         }
     }
 
     public void send_control(String route, String lane) {
         var car_lane_queue = lane_queue.get(route).get(lane);
         for (String car : car_lane_queue) {
             // Check if the car is in the range of the RSU
             // If it is not in the range, send a message to the nearest Vehicle
             var routing = routingTo_car(car, route, lane);
             if (routing != null){
                 var control = build_control(route, lane, car);
                 getOs().getAdHocModule().sendV2xMessage(new GreenWaveMsg(routing, new RawPayload(control, car)));
                 getLog().infoSimTime(this, "Sent control message to " + car);
             }
             else{
                 getLog().infoSimTime(this, "No car found to send control message");
             }
         }      
     }
 
     private boolean isRouteGreen(String route) {
         // Check all lanes of the route if they are green
         for (Map.Entry<String, TL.Status> lane : traffic_light.trafficLightStatus.get(route).entrySet()) {
             if (lane.getValue() != TL.Status.GREEN) {
                 return false;
             }
         }
         return true;
     }
     
     private void setRouteState(String route, String state) {
         // Change the state of all lanes of the route
         for (Map.Entry<String, TL.Status> lane : traffic_light.trafficLightStatus.get(route).entrySet()) {
             traffic_light.change_state(route, lane.getKey(), TL.Status.valueOf(state));
         }
     }
     
     private boolean laneQueueIsHeavy(String route, String lane) {
         // Check if the lane queue is above the threshold
         return lane_queue.get(route).get(lane).size() > LANE_THRESHOLD;
     }
 
    public void manage_traffic() {
        var time_now = getOs().getSimulationTime();
        if (time_now - last_state_change > TRAFFIC_TIMER) {
            getLog().infoSimTime(this, "Changing traffic light state");
    
            Set<String> activeRoutes = new HashSet<>();
            // Check and update traffic lights based on the given rules
    
            // Rule: If route 3 is green then route 4,5,6 are red and vice versa
            if (isRouteGreen("3")) {
                setRouteState("4", "RED");
                setRouteState("5", "RED");
                setRouteState("6", "RED");
            } else if (isRouteGreen("4") || isRouteGreen("5") || isRouteGreen("6")) {
                setRouteState("3", "RED");
            }
    
            // Rule: If route 1,2 are green then route 4,5 are red and vice versa
            if (isRouteGreen("1") && isRouteGreen("2")) {
                setRouteState("4", "RED");
                setRouteState("5", "RED");
            } else if (isRouteGreen("4") || isRouteGreen("5")) {
                setRouteState("1", "RED");
                setRouteState("2", "RED");
            }
    
            // Rule: If route 1,2 are green then route 6 can be green
            if (isRouteGreen("1") && isRouteGreen("2")) {
                setRouteState("6", "GREEN");
            }
    
            // Rule: Route 1,2,3 can be green at the same time
            if (isRouteGreen("1") && isRouteGreen("2") && isRouteGreen("3")) {
                setRouteState("1", "GREEN");
                setRouteState("2", "GREEN");
                setRouteState("3", "GREEN");
            }
    
            // Rule: Route 4,5,6 can be green at the same time
            if (isRouteGreen("4") && isRouteGreen("5") && isRouteGreen("6")) {
                setRouteState("4", "GREEN");
                setRouteState("5", "GREEN");
                setRouteState("6", "GREEN");
            }
    
            // Rule: If route 6 is green then route 3 is red and vice versa
            if (isRouteGreen("6")) {
                setRouteState("3", "RED");
            } else if (isRouteGreen("3")) {
                setRouteState("6", "RED");
            }

            // Adjust states based on lane queues (simplified logic, adapt as needed)
            for (Map.Entry<String, Map<String, Set<String>>> route : lane_queue.entrySet()) {
                for (Map.Entry<String, Set<String>> lane : route.getValue().entrySet()) {
                    if (laneQueueIsHeavy(route.getKey(), lane.getKey())) {
                        traffic_light.setGreen(route.getKey(), lane.getKey());
                        activeRoutes.add(route.getKey());
                    } else {
                        traffic_light.setRed(route.getKey(), lane.getKey());
                    }
                }
            }
            // Ensure traffic lights are updated
            for (String route : activeRoutes) {
                for (int lane = 0; lane < 10; lane++){
                    send_control(route, String.valueOf(lane));
                }
            }
            last_state_change = time_now;
        }
    }
 
     @Override
     public void onStartup() {
         getLog().infoSimTime(this, "Initialize application");
         getOs().getAdHocModule().enable(new AdHocModuleConfiguration()
                 .addRadio()
                 .channel(AdHocChannel.CCH)
                 .power(20)
                 .distance(300) // Dunno why
                 .create());
         getLog().infoSimTime(this, "Activated WLAN Module");
         sample();
 
         last_state_change = getOs().getSimulationTime();
 
         for (int route = 1; route < 7; route++) {
             lane_queue.put(String.valueOf(route), new HashMap<String, Set<String>>());
             for (int lane = 0; lane < 10; lane++) {
                 lane_queue.get(String.valueOf(route)).put(String.valueOf(lane), (Set<String>) new HashSet<String>());
             }
         }
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
 
         if (raw.payload instanceof InDetectionZone) {
             if (send_to.equals(RSU_ID)) {
                 InDetectionZone its_britney = (InDetectionZone) receivedMessage.getMessage().payload;
                 if (receivedV2xMessage.getMessage().getRouting().getSource().getSourcePosition()
                 .distanceTo(getOs().getPosition()) > MAX_DISTANCE_RANGE){
                     car_table.remove(its_britney.id);
                     return;
                 }
                 getLog().infoSimTime(this, "Received InDetectionZone message from " + its_britney.id + " at route " + its_britney.route + " and lane " + its_britney.lane);
                 
                 var route = String.valueOf(its_britney.route);
                 var lane = String.valueOf(its_britney.lane);
                 if (lane_queue.get(route).containsKey(lane)) {
                     lane_queue.get(route).get(lane).add(its_britney.id);
 
                 } else {
                     lane_queue.get(route).put(lane, (Set<String>) new HashSet<String>());
                     lane_queue.get(route).get(lane).add(its_britney.id);
                 }
             }
         }
         if (raw.payload instanceof CAM) {
             var cam = (CAM) receivedMessage.getMessage().payload;
             if (receivedV2xMessage.getMessage().getRouting().getSource().getSourcePosition().distanceTo(getOs().getPosition()) > MAX_DISTANCE_RANGE){
                 car_table.remove(cam.id);
                 return;
             }
             car_table.put(cam.id, cam);
             getLog().infoSimTime(this, "Received CAM message from " + cam.id + " at route " + cam.route + " and lane " + cam.lane);
 
             // Check if the car is moving towards the RSU/TL or away from it
             if (!cam.isMovingTowards){
                 // If the car is moving away from the RSU/TL, remove it from the queues and the car_table
                 if (car_information.containsKey(cam.id)){
                     var car_request = car_information.get(cam.id);
                     var route = String.valueOf(car_request.route);
                     var lane = String.valueOf(car_request.lane);
                     if (car_table.containsKey(route)){
                         if (lane_queue.get(route).containsKey(lane)){
                             lane_queue.get(route).get(lane).remove(cam.id);
                         }
                     }
                 }
             }
 
             
         }
     }
 
     @Override
     public void onShutdown() {
         getLog().infoSimTime(this, "Shutdown application");
     }
 
     @Override
     public void onAcknowledgementReceived(ReceivedAcknowledgement arg0) {
     }
 
     @Override
     public void onCamBuilding(CamBuilder arg0) {
     }
 
     @Override
     public void onMessageTransmitted(V2xMessageTransmission arg0) {    
     }
 }
 