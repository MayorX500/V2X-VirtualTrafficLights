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

import org.eclipse.mosaic.lib.geo.GeoPoint;
import org.eclipse.mosaic.lib.geo.MutableGeoPoint;

public class ItsBritneyBitch implements Serializable{
    private static final int hash = 853508629;
    private static final long serialVersionUID = 1L;
    public String message = "It's Britney Bitch!";
    public int TTL = 6;
    public String id;
    public int route;
    public int lane;
    public GeoPoint position;

    public ItsBritneyBitch(String id,int route, int lane, GeoPoint position) {
        this.route = route;
        this.lane = lane;
        this.position = position;
    }

    public ItsBritneyBitch(){
        this.id = "veh_x";
        this.route = 0;
        this.lane = 0;
        this.position = new MutableGeoPoint(0, 0);
    }
    
    @Override
    public int hashCode(){
        return hash;
    }

    
}
