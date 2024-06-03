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
            trafficLightStatus.put(String.valueOf(route), new HashMap<>());
            for (int lane = 0; lane < 10; lane++) { // it has more lanes than the real road
                if (route > 3)
                    trafficLightStatus.get(String.valueOf(route)).put(String.valueOf(lane), TL.Status.GREEN);
                else
                    trafficLightStatus.get(String.valueOf(route)).put(String.valueOf(lane), TL.Status.RED);
            }
        }
    }

    public boolean reverse_state(String route, String lane) {
        if (trafficLightStatus.containsKey(route) && trafficLightStatus.get(route).containsKey(lane)) {
            if (trafficLightStatus.get(route).get(lane) == TL.Status.RED) {
                trafficLightStatus.get(route).put(lane, TL.Status.GREEN);
            }
            else {
                trafficLightStatus.get(route).put(lane, TL.Status.RED);
            }
            return true;
        }
        else
            return false;
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
        for (HashMap.Entry<String, Map<String, TL.Status>> entry : trafficLightStatus.entrySet()) {
            for (HashMap.Entry<String, TL.Status> inner_entry : entry.getValue().entrySet()) {
                if (!tl_state.containsKey(entry.getKey())) {
                    tl_state.put(entry.getKey(), new HashMap<>());
                }
                tl_state.get(entry.getKey()).put(inner_entry.getKey(), inner_entry.getValue());
            }
        }
        return tl_state;
    }

    
}
