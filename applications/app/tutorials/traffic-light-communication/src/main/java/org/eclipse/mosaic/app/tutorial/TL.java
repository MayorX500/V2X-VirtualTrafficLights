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
import java.util.HashMap;

public class TL implements Serializable {
    private static final int hash = 671474557;

    public enum Status {

        GREEN, RED, YELLOW, BLINKING;

        public String toString() {
            return this.name();
        }

        public static Status fromString(String status) {
            return Status.valueOf(status);
        }
    }

    private static final long serialVersionUID = 1L;
    public int TTL = 6;
    public String id;
    public HashMap<String,HashMap<String, Status>>state; // Route, Lane, Status
    public boolean isOff;

    public TL(String id, HashMap<String,HashMap<String, Status>> state, boolean isOff) {
        this.id = id;
        this.state = state;
        this.isOff = isOff;
    }

    public TL(String id, boolean isOff) {
        this.id = id;
        this.state = new HashMap<>();
        this.isOff = isOff;
    }

    public TL() {
        this.id = "0";
        this.state = new HashMap<>();
        this.isOff = false;
    }

    @Override
    public int hashCode() {
        return hash;
    }

    public String toString() {
        return "TL{" +
                "id='" + id + '\'' +
                ", state=" + state +
                ", isOff=" + isOff +
                '}';
    }
    
}
