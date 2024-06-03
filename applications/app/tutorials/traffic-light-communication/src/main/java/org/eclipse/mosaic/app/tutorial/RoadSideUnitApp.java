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
import org.eclipse.mosaic.lib.geo.GeoPoint;
import org.eclipse.mosaic.lib.geo.MutableGeoPoint;
import org.eclipse.mosaic.lib.objects.addressing.AdHocMessageRoutingBuilder;
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
     * Interval at which messages are sent (every 0.25 seconds).
     */
        private final static long TIME_INTERVAL = TIME.SECOND / 4;
    private final static String RSU_ID = "rsu_0";
    private final static Integer MAX_DISTANCE_RANGE = 30;
    private final static Integer MAX_RSU_DISTANCE = 70;
    private final static long MAX_MESSAGE_WAIT = TIME.SECOND * 100;

    private final static int CAM_HASH = new CAM().hashCode();
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

    public Map<String,CAM> car_table = new HashMap<String,CAM>();
    public Map<String,ItsBritneyBitch> car_information = new HashMap<String,ItsBritneyBitch>();
    public boolean is_off = false;
    public Map<String,Map<String,Set<String>>> lane_queue = new HashMap<String,Map<String,Set<String>>>();
    public TrafficLight traffic_light = new TrafficLight();

    public void sample() {
        getOs().getEventManager().addEvent(
                getOs().getSimulationTime() + TIME_INTERVAL, this
        );
        sendState();
        // TODO: RUN TRAFFIC LIGHT MANAGER
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

    public void send_control(String route, String lane) {
        AdHocMessageRoutingBuilder routing = getOperatingSystem().getAdHocModule().createMessageRouting();

        var car_lane_queue = lane_queue.get(route).get(lane);
        for (String car : car_lane_queue) {

            // Check if the car is in the range of the RSU
            // If it is not in the range, send a message to the nearest Vehicle
        }

        
    }

    public void manage_traffic() {
        // Check if there are cars in the queue

        for (Map.Entry<String, Map<String, Set<String>>> route : lane_queue.entrySet()) {
            for (Map.Entry<String, Set<String>> lane : route.getValue().entrySet()) {
                if (lane.getValue().size() > 0) {
                    // TODO: Implement traffic management based on the queues
                    // Check if the traffic light is green
                    // If it is green send a controllet message to the cars in the queue
                    // 
                }
            }
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

        if (raw.payload instanceof ItsBritneyBitch) {
            if (send_to.equals(RSU_ID)) {
                ItsBritneyBitch its_britney = (ItsBritneyBitch) receivedMessage.getMessage().payload;
                if (receivedV2xMessage.getMessage().getRouting().getSource().getSourcePosition()
                .distanceTo(getOs().getPosition()) > MAX_DISTANCE_RANGE){
                    car_table.remove(its_britney.id);
                    return;
                }
                
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
