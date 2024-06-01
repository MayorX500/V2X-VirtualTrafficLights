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

public class TrafficLight {
    public Map<String, Map<String,TL.Status>> trafficLightStatus = new HashMap<>();

    public TrafficLight() {
        for (int route = 1; route < 7 ; route++) {
            for (int lane = 0; lane < 10; lane++) { // it has more lanes than the real road
                trafficLightStatus.put("" + route, new HashMap<>());
                trafficLightStatus.get("" + route).put("" + lane, TL.Status.RED);
            }
        }
    }

    public boolean change_state(String route, String lane, TL.Status status) {
        if (trafficLightStatus.containsKey(route) && trafficLightStatus.get(route).containsKey(lane)) {
            trafficLightStatus.get(route).put(lane, status);
            return true;
        }
        else
            return false;
    }

    public TL.Status get_state(String route, String lane) {
        if (trafficLightStatus.containsKey(route) && trafficLightStatus.get(route).containsKey(lane)) {
            return trafficLightStatus.get(route).get(lane);
        }
        else
            return TL.Status.RED;
    }

    public HashMap<String,HashMap<String, TL.Status>> get_state() {
        HashMap<String, HashMap<String, TL.Status>> tl_state = new HashMap<>();
        for (String route : trafficLightStatus.keySet()) {
            tl_state.put(route, new HashMap<>());
            for (String lane : trafficLightStatus.get(route).keySet()) {
                tl_state.get(route).put(lane, trafficLightStatus.get(route).get(lane));
            }
        }
        return tl_state;
    }

    
}
