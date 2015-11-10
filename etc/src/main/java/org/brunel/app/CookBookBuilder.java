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
package org.brunel.app;

public class CookBookBuilder extends DocBuilder {

	private static final String ITEM_FORMAT = "**[%s](%s)** %s\n\n`%s`\n\n";


    public static final String BASIC = "/org/brunel/app/basic.txt";
    public static final String STATS = "/org/brunel/app/stats.txt";
    public static final String INFO_VIS = "/org/brunel/app/infovis.txt";

    public static void main(String[] args) throws Exception {

        new CookBookBuilder().run();

    }

    private void run() throws Exception {
    	out.append("***\n");
    	out.append("### Basic Charts\n");
    	run(BASIC, ITEM_FORMAT);
    	out.append("***\n");
    	out.append("### Statistical Graphics\n");
    	run(STATS, ITEM_FORMAT);
    	out.append("***\n");
    	out.append("### Information Visualization\n");
    	run(INFO_VIS, ITEM_FORMAT);
    	System.out.println(out.toString());
        display.showInBrowser();


    }


	@Override
	protected String format(String itemFormat, String target,
			String description, String image, String title, String brunel) {
		return String.format(itemFormat, title, target, description, brunel);
	}

}
