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
import java.util.List;

import org.apache.commons.lang3.Validate;
import org.eclipse.mosaic.app.tutorial.message.GreenWaveMsg;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.CamBuilder;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.ReceivedAcknowledgement;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.ReceivedV2xMessage;
import org.eclipse.mosaic.fed.application.app.AbstractApplication;
import org.eclipse.mosaic.fed.application.app.api.CommunicationApplication;
import org.eclipse.mosaic.fed.application.app.api.os.TrafficLightOperatingSystem;
import org.eclipse.mosaic.interactions.communication.V2xMessageTransmission;
import org.eclipse.mosaic.lib.geo.GeoPoint;
import org.eclipse.mosaic.lib.geo.MutableGeoPoint;
import org.eclipse.mosaic.lib.util.scheduling.Event;
import org.eclipse.mosaic.rti.TIME;


public final class TrafficLightApp extends AbstractApplication<TrafficLightOperatingSystem> implements CommunicationApplication {
    public final static String SECRET = "open sesame!";
    private final static long TIME_INTERVAL = TIME.SECOND / 100;
    private final static short GREEN_DURATION = 20; // Time to keep the traffic light green dunno if it's in seconds

    private final static GeoPoint ROUTE1_GEO_POINT_S = new MutableGeoPoint(40.743507100880656, -73.98839394324335);
    private final static GeoPoint ROUTE1_GEO_POINT_E = new MutableGeoPoint(40.7435594287452, -73.98834968677761);

    private final static GeoPoint ROUTE2_GEO_POINT_S = new MutableGeoPoint(40.74357721003281, -73.98821423490355);
    private final static GeoPoint ROUTE2_GEO_POINT_E = new MutableGeoPoint(40.7435152293083, -73.98807274823156);


    

    private static final String DEFAULT_PROGRAM = "0"; // Default program id for traffic lights
    private static final String GREEN_PROGRAM = "2"; // Green program id for traffic lights

    private static final Integer MIN_DISTANCE = 20; // Minimum distance for cars to be considered in range

    public List<String> cars_received = new ArrayList<String>(); // List of cars that have sent a message, are in range, and waiting the traffic light to turn green
    public boolean event_created = false; // Flag to check if the event to change back to red has been created
    
    public static boolean isPointBetween(GeoPoint pointA, GeoPoint pointB, GeoPoint pointToCheck) {
        double minLatitude = Math.min(pointA.getLatitude(), pointB.getLatitude());
        double maxLatitude = Math.max(pointA.getLatitude(), pointB.getLatitude());
        double minLongitude = Math.min(pointA.getLongitude(), pointB.getLongitude());
        double maxLongitude = Math.max(pointA.getLongitude(), pointB.getLongitude());

        return (pointToCheck.getLatitude() >= minLatitude && pointToCheck.getLatitude() <= maxLatitude) &&
               (pointToCheck.getLongitude() >= minLongitude && pointToCheck.getLongitude() <= maxLongitude);
    }

    private void sendStatusMessage() {
        
    }


    @Override
    public void onStartup() {
        getLog().infoSimTime(this, "Initialize application");
        getOs().getAdHocModule().enable();
        getLog().infoSimTime(this, "Activated Wifi Module");
        getLog().infoSimTime(this, getOs().getAllTrafficLights().toString());
        setRed();
    }

    @Override
    public void onShutdown() {
        getLog().infoSimTime(this, "Shutdown application");
    }

    private void setGreen() {

        if (cars_received.size() >= 2) {
            getOs().switchToProgram(GREEN_PROGRAM);
            getLog().infoSimTime(this, "Setting traffic lights to GREEN");
            event_created = false;
        }

        if (!event_created) {
            event_created = true;
            getOs().getEventManager().addEvent(
                getOs().getSimulationTime() + GREEN_DURATION * TIME.SECOND,
                (e) -> setRed()
            );
        }
    }

    private void setRed() {
        getOs().switchToProgram(DEFAULT_PROGRAM);
        getLog().infoSimTime(this, "Setting traffic lights to RED");
    }

    public static void clearScreen() {  
        System.out.print("\033[H\033[2J");  
        System.out.flush();  
    }  

    @Override
    public void onMessageReceived(ReceivedV2xMessage receivedV2xMessage) {
        if (!(receivedV2xMessage.getMessage() instanceof GreenWaveMsg)) {
            return;
        }
        getLog().infoSimTime(this, "Received GreenWaveMsg");

        Validate.notNull(receivedV2xMessage.getMessage().getRouting().getSource().getSourcePosition(),
                "The source position of the sender cannot be null");
        if (!(receivedV2xMessage.getMessage().getRouting().getSource().getSourcePosition()
                .distanceTo(getOs().getPosition()) <= MIN_DISTANCE)) {
            cars_received.remove(receivedV2xMessage.getMessage().getRouting().getSource().getSourceAddress().toString());
            getLog().infoSimTime(this, "Vehicle that sent message is too far away.");
            return;
        }

        if (DEFAULT_PROGRAM.equals(getOs().getCurrentProgram().getProgramId())) {
            if (!cars_received.contains(receivedV2xMessage.getMessage().getRouting().getSource().getSourceAddress().toString())) {
                cars_received.add(receivedV2xMessage.getMessage().getRouting().getSource().getSourceAddress().toString());
            }
            //System.out.println("LANES - " + getOperatingSystem().getControlledLanes().toString());

            System.out.println(((GreenWaveMsg) receivedV2xMessage.getMessage()).getMessage().toString());

            setGreen();
        }

    }

    private void sample() {
        getOs().getEventManager().addEvent(
                getOs().getSimulationTime() + TIME_INTERVAL, this
        );
        sendStatusMessage();
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

    @Override
    public void processEvent(Event event) throws Exception {
        // nop
    }
}

