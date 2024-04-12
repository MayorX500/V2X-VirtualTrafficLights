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
import org.eclipse.mosaic.lib.objects.trafficlight.TrafficLightState;
import org.eclipse.mosaic.lib.util.scheduling.Event;
import org.eclipse.mosaic.rti.TIME;
import org.eclipse.mosaic.app.tutorial.message.GreenWaveMsg;


public final class TrafficLightApp extends AbstractApplication<TrafficLightOperatingSystem> implements CommunicationApplication {
    public final static String SECRET = "open sesame!";
    private final static short GREEN_DURATION = 20; // Time to keep the traffic light green dunno if it's in seconds

    private static final String DEFAULT_PROGRAM = "1"; // Default program id for traffic lights
    private static final String GREEN_PROGRAM = "0"; // Green program id for traffic lights

    private static final Integer MIN_DISTANCE = 20; // Minimum distance for cars to be considered in range

    public List<String> cars_received = new ArrayList<String>(); // List of cars that have sent a message, are in range, and waiting the traffic light to turn green
    public boolean event_created = false; // Flag to check if the event to change back to red has been created
    
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

        /*
        String traffic_id = getOs().getId();

        if (traffic_id.equals("tl_19")) {
            getOs().switchToProgram(GREEN_PROGRAM);
        }
        else
        if (traffic_id.equals("tl_14")) {
            getOs().switchToProgram(GREEN_PROGRAM);
        }
        else
        */
        

        if (cars_received.size() >= 5) {
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

        //getOs().switchToProgram(GREEN_PROGRAM);

		//getOs().switchToCustomState(trafficLightStates);
        
        //System.out.println("Get Current Phase:");
        //System.out.println(getOs().getCurrentPhase().toString());
        //System.out.println("Get Controlled Lanes:");
        //System.out.println(getOs().getControlledLanes().toString());
        //System.out.println("Get Current Program:");
        //System.out.println(getOs().getCurrentProgram().toString());
        //System.out.println("Get Traffic ID:");
        //System.out.println(getOs().getId().toString());


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
        if (getOs().getId().equals("tl_19")){ // Only for traffic light 19
            System.out.println("Traffic Light: " + getOs().getId() + "\nNumber of cars: " + cars_received.size());
            System.out.println(cars_received.toString());
            getLog().infoSimTime(this, "Traffic Light: " + getOs().getId() + "\nNumber of cars: " + cars_received.size());
        }
        if (!(receivedV2xMessage.getMessage() instanceof GreenWaveMsg)) {
            return;
        }
        getLog().infoSimTime(this, "Received GreenWaveMsg");

        getLog().infoSimTime(this, "Received Vehicle ID: {}", SECRET);

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
            setGreen();
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

    @Override
    public void processEvent(Event event) throws Exception {
        // nop
    }
}

