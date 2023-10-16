/**
 * Copyright (c) 2004-2011 QOS.ch
 * All rights reserved.
 *
 * Permission is hereby granted, free  of charge, to any person obtaining
 * a  copy  of this  software  and  associated  documentation files  (the
 * "Software"), to  deal in  the Software without  restriction, including
 * without limitation  the rights to  use, copy, modify,  merge, publish,
 * distribute,  sublicense, and/or sell  copies of  the Software,  and to
 * permit persons to whom the Software  is furnished to do so, subject to
 * the following conditions:
 *
 * The  above  copyright  notice  and  this permission  notice  shall  be
 * included in all copies or substantial portions of the Software.
 *
 * THE  SOFTWARE IS  PROVIDED  "AS  IS", WITHOUT  WARRANTY  OF ANY  KIND,
 * EXPRESS OR  IMPLIED, INCLUDING  BUT NOT LIMITED  TO THE  WARRANTIES OF
 * MERCHANTABILITY,    FITNESS    FOR    A   PARTICULAR    PURPOSE    AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE,  ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 */
package com.taoensso.telemere.slf4j;
// Based on `org.slf4j.simple.SimpleLogger`

import java.io.Serializable;

import org.slf4j.Logger;
import org.slf4j.Marker;
import org.slf4j.event.Level;
import org.slf4j.event.LoggingEvent;
import org.slf4j.helpers.LegacyAbstractLogger;
import org.slf4j.spi.LoggingEventAware;

import clojure.java.api.Clojure;
import clojure.lang.IFn;

public class TelemereLogger extends LegacyAbstractLogger implements LoggingEventAware, Serializable {

    private static final long serialVersionUID = -1999356203037132557L;

    private static boolean INITIALIZED = false;
    static void lazyInit() {
        if (INITIALIZED) { return; }
        INITIALIZED = true;
        init();
    }

    private static IFn logFn;
    private static IFn isLevelEnabledFn;

    static void init() {
	IFn requireFn =    Clojure.var("clojure.core", "require");
	requireFn.invoke( Clojure.read("taoensso.telemere.slf4j"));
	logFn =            Clojure.var("taoensso.telemere.slf4j", "log!");
	isLevelEnabledFn = Clojure.var("taoensso.telemere.slf4j", "allowed?");
    }

    protected TelemereLogger(String name) { this.name = name; }

    protected boolean isLevelEnabled(Level level) { return (boolean) isLevelEnabledFn.invoke(level);       }
    public    boolean isTraceEnabled()            { return (boolean) isLevelEnabledFn.invoke(Level.TRACE); }
    public    boolean isDebugEnabled()            { return (boolean) isLevelEnabledFn.invoke(Level.DEBUG); }
    public    boolean  isInfoEnabled()            { return (boolean) isLevelEnabledFn.invoke(Level.INFO);  }
    public    boolean  isWarnEnabled()            { return (boolean) isLevelEnabledFn.invoke(Level.WARN);  }
    public    boolean isErrorEnabled()            { return (boolean) isLevelEnabledFn.invoke(Level.ERROR); }

    public void log(LoggingEvent event) { logFn.invoke(event); } // Fluent (modern) API, called after level check

    @Override protected String getFullyQualifiedCallerName() { return null; }
    @Override
    protected void handleNormalizedLoggingCall(Level level, Marker marker, String messagePattern, Object[] arguments, Throwable throwable) {
	logFn.invoke(level, throwable, messagePattern, arguments, marker); // Legacy API, called after level check
    }

}
