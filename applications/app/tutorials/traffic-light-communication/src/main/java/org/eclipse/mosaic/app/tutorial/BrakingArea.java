/*
 * Copyright (c) 2024 Fraunhofer FOKUS and others. All rights reserved.
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

import org.eclipse.mosaic.lib.geo.GeoPoint;

public class BrakingArea {

    // Square defined by two points (top left and bottom right)
    public GeoPoint pointA;
    public GeoPoint pointB;

    public BrakingArea(GeoPoint pointA, GeoPoint pointB) {
        this.pointA = pointA;
        this.pointB = pointB;
    }

    public boolean isPointInsideSquare(GeoPoint point) {
        double minLat = Math.min(this.pointA.getLatitude(), this.pointB.getLatitude());
        double maxLat = Math.max(this.pointA.getLatitude(), this.pointB.getLatitude());
        double minLon = Math.min(this.pointA.getLongitude(), this.pointB.getLongitude());
        double maxLon = Math.max(this.pointA.getLongitude(), this.pointB.getLongitude());

        double pointLat = point.getLatitude();
        double pointLon = point.getLongitude();

        return (pointLat >= minLat && pointLat <= maxLat && pointLon >= minLon && pointLon <= maxLon);
    }




    
}
