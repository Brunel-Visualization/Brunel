package org.brunel.describe;

import org.brunel.action.Action;
import org.brunel.model.VisItem;
import org.brunel.util.WebDisplay;

/**
 * A demonstration application that shows hwo to use Brunel rapidly
 */
public class SampleApp {

    /**
     * Run a simple Brunel command.
     * The first parameter is a Brunel Command. The second is the URL of the data source. This can be a file reference
     * such as 'file://...'.
     * When run it will build a HTML page with the full Brunel in it, and display it in the default browser
     * @param args specify the command
     */
    public static void main(String[] args) {

        // Process the commands:
        String command = "bubble size(#count) color(country) label(country, #count) tooltip(brand) list(brand)";
        if (args.length > 0) command = args[0];

        String source = "http://brunel.mybluemix.net/sample_data/whiskey.csv";
        if (args.length > 1) source = args[1];

        // Assembe the two parts into one complete brunel command, with data and visualziation actions
        String fullCommand = "data('" + source + "') " + command;

        // Build the action and apply it to create the VisItem
        Action action = Action.parse(fullCommand);
        VisItem vis = action.apply();

        // Write out the HTML to show the vis item, and then call the system browser to show it
        WebDisplay display = new WebDisplay("sample-app");
        display.buildSingle(vis, 800, 600, "index.html", "Brunel Sample Application");
        display.showInBrowser();

    }
}
