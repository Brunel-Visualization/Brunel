/*
 * Copyright (c) 2015 IBM Corporation and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.brunel.model;

/*
 * Any form of exception thrown by Brunel and being surfaced outside the package
 */
public class VisException extends RuntimeException {

    public static VisException makeApplying(Throwable cause, Object action) {
        return cause instanceof VisException ? (VisException) cause :
                new VisException("applying action", cause, action);
    }

    public static VisException makeBuilding(Throwable cause, VisItem item) {
        return cause instanceof VisException ? (VisException) cause :
                new VisException("building the visualization", cause, item);
    }

    public static VisException makeParsing(Throwable cause, String action) {
        return cause instanceof VisException ? (VisException) cause :
                new VisException("parsing action text", cause, action);
    }

    private final String info;
    private final Throwable cause;
    private final Object source;

    private VisException(String info, Throwable cause, Object source) {
        super(makeMessage(cause) +  " while " + info + ": " + source, cause);
        this.info = info;
        this.cause = cause;
        this.source = source;
        setStackTrace(cause.getStackTrace());
    }

    private static String makeMessage(Throwable cause) {
        String message = "A programming error caused an exception";
        if (cause != null && cause.getMessage() != null) {
            String s = cause.getMessage().replaceAll("\n", ". ").trim();
            if (s.length() > 5) message = s;
        }
        if (message.endsWith(".")) message  = message.substring(0, message.length()-1).trim();
        return message;
    }

    public Object getSource() {
        return source;
    }

    public String getType() {
        return info;
    }

    public String getShortMessage() {
        return makeMessage(cause);
    }
}
