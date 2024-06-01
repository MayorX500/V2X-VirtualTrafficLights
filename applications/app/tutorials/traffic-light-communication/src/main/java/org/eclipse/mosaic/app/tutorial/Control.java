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

public class Control implements Serializable {
    private static final int hash = 381307639;

    public enum Rule {
        STOP, GO, SLOW_DOWN;

        public String toString() {
            return this.name();
        }

        public static Rule fromString(String rule) {
            return Rule.valueOf(rule);
        }

    }

    private static final long serialVersionUID = 1L;
    public int TTL = 6;
    public String to_who;
    public Rule rule;
    
    public Control(String to_who, Rule rule) {
        this.to_who = to_who;
        this.rule = rule;
    }

    public Control(String to_who, String rule) {
        this.to_who = to_who;
        this.rule = Rule.fromString(rule);
    }

    public Control() {
        this.to_who = "0";
        this.rule = Rule.STOP;
    }

    public String toString() {
        return "Control{" +
                "to_who='" + to_who + '\'' +
                ", rule=" + rule.toString() +
                '}';
    }

    @Override
    public int hashCode() {
        return hash;
    }
}
