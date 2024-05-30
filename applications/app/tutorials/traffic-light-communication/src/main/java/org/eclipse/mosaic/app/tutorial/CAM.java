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

import java.io.Serializable;

import org.eclipse.mosaic.lib.enums.DriveDirection;

// id, velocidade, direção, aceleração, lane, rota
public class CAM implements Serializable {
    private static final long serialVersionUID = 1L;
    public String id; // vehicle id
    public double speed; // m/s
    public DriveDirection direction; // 0 = forward, 1 = backward
    public boolean isMovingTowards; // true if the vehicle is moving towards the traffic light
    public double acceleration; // m/s^2
    public int lane; // lane number
    public String route; // route id

    public CAM(String id, double speed, DriveDirection direction, boolean isMovingTowards, double acceleration, int lane, String route) {
        this.id = id;
        this.speed = speed;
        this.direction = direction;
        this.isMovingTowards = isMovingTowards;
        this.acceleration = acceleration;
        this.lane = lane;
        this.route = route;
    }

    public CAM(){
        this.id = "0";
        this.speed = 0;
        this.direction = DriveDirection.FORWARD;
        this.isMovingTowards = false;
        this.acceleration = 0;
        this.lane = 0;
        this.route = "0";
    }

    public String toString() {
        return "CAM{" +
                "id='" + id + '\'' +
                ", speed=" + speed +
                ", direction=" + direction +
                ", isMovingTowards=" + isMovingTowards +
                ", acceleration=" + acceleration +
                ", lane=" + lane +
                ", route='" + route + '\'' +
                '}';
    }
    

    
}
