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

package org.brunel.data;

import java.util.ArrayList;
import java.util.List;

public class CannedData {

    public static final String bank = "gender,bdate,educ,jobcat,salary,salbegin,jobtime,minority\n"
            + "Male,19027,15,Manager,57000,27000,98,No\n"
            + "Male,21328,16,Clerical,40200,18750,98,No\n"
            + "Female,10800,12,Clerical,21450,12000,98,No\n"
            + "Female,17272,8,Clerical,21900,13200,98,No\n"
            + "Male,20129,15,Clerical,45000,21000,98,No\n"
            + "Male,21419,15,Clerical,32100,13500,98,No\n"
            + "Male,20571,15,Clerical,36000,18750,98,No\n"
            + "Female,24233,12,Clerical,21900,9750,98,No\n"
            + "Female,16825,15,Clerical,27900,12750,98,No\n"
            + "Female,16846,12,Clerical,24000,13500,98,No\n"
            + "Female,18301,16,Clerical,30300,16500,98,No\n"
            + "Male,24118,8,Clerical,28350,12000,98,Yes\n"
            + "Male,22114,15,Clerical,27750,14250,98,Yes\n"
            + "Female,17955,15,Clerical,35100,16800,98,Yes\n"
            + "Male,22887,12,Clerical,27300,13500,97,No\n"
            + "Male,23698,12,Clerical,40800,15000,97,No\n"
            + "Male,22845,15,Clerical,46000,14250,97,No\n"
            + "Male,20534,16,Manager,103750,27510,97,No\n"
            + "Male,22877,12,Clerical,42300,14250,97,No\n"
            + "Female,14633,12,Clerical,26250,11550,97,No\n"
            + "Female,23061,16,Clerical,38850,15000,97,No\n"
            + "Male,14878,12,Clerical,21750,12750,97,Yes\n"
            + "Female,23816,15,Clerical,24000,11100,97,Yes\n"
            + "Female,12140,12,Clerical,16950,9000,97,Yes\n"
            + "Female,15523,15,Clerical,21150,9000,97,Yes";

    public static final String movies = "Title,US Gross,Worldwide Gross,Production Budget,Release Date,MPAA Rating,Running Time (min),Distributor,Source,Major Genre,Creative Type,Director,Rotten Tomatoes Rating,IMDB Rating,IMDB Votes\n"
            + "The Godfather,134966411,268500000,7000000,3/15/72,,,Paramount Pictures,,,Historical Fiction,Francis Ford Coppola,100,9.2,411088\n"
            + "The Exorcist,204632868,402500000,12000000,12/26/73,R,,Warner Bros.,Based on Book/Short Story,Horror,Contemporary Fiction,William Friedkin,84,8.1,103131\n"
            + "Jaws,260000000,470700000,12000000,6/20/75,PG,,Universal,Based on Book/Short Story,Horror,Contemporary Fiction,Steven Spielberg,100,8.3,138017\n"
            + "Rocky,117235147,225000000,1000000,11/21/76,,,MGM,,,,John G. Avildsen,,4,84\n"
            + "Star Wars Ep. IV: A New Hope,460998007,797900000,11000000,5/25/77,PG,,20th Century Fox,Original Screenplay,Adventure,Science Fiction,George Lucas,,,\n"
            + "The Spy Who Loved Me,46800000,185400000,14000000,7/13/77,,,MGM,Based on Book/Short Story,Action,Contemporary Fiction,,78,7.1,24938\n"
            + "Close Encounters of the Third Kind,166000000,337700000,20000000,11/16/77,,,Sony Pictures,Original Screenplay,Adventure,Science Fiction,Steven Spielberg,95,7.8,59049\n"
            + "Jaws 2,102922376,208900376,20000000,6/16/78,PG,,Universal,Based on Book/Short Story,Horror,Contemporary Fiction,,56,5.6,18793\n"
            + "Grease,305260,206005260,6000000,6/16/78,PG,,Paramount Pictures,Based on Musical/Opera,Musical,Historical Fiction,Randal Kleiser,83,7,60146\n"
            + "Superman,134218018,300200000,55000000,12/15/78,PG,,Warner Bros.,Based on Comic/Graphic Novel,Adventure,Super Hero,Richard Donner,94,4.9,129\n"
            + "Alien,80930630,203630630,9000000,5/25/79,R,,20th Century Fox,Original Screenplay,Horror,Science Fiction,Ridley Scott,97,8.5,180387\n"
            + "Moonraker,70300000,210300000,31000000,6/29/79,,,MGM,Based on Book/Short Story,Action,Contemporary Fiction,,64,6.1,26760\n"
            + "Star Wars Ep. V: The Empire Strikes Back,290271960,534171960,23000000,5/21/80,PG,,20th Century Fox,Original Screenplay,Adventure,Science Fiction,,,,\n"
            + "Raiders of the Lost Ark,245034358,386800358,20000000,6/12/81,PG,,Paramount Pictures,Original Screenplay,Adventure,Historical Fiction,Steven Spielberg,,8.7,242661\n"
            + "For Your Eyes Only,54800000,195300000,28000000,6/26/81,,,MGM,Based on Book/Short Story,Action,Contemporary Fiction,John Glen,71,6.8,23527\n"
            + "ET: The Extra-Terrestrial,435110554,792910554,10500000,6/11/82,PG,,Universal,Original Screenplay,Drama,Science Fiction,Steven Spielberg,,7.9,105028\n"
            + "Tootsie,177200000,177200000,15000000,12/17/82,,,Sony Pictures,Original Screenplay,Comedy,Contemporary Fiction,Sydney Pollack,87,7.4,31669\n"
            + "Flashdance,90463574,201463574,7000000,4/15/83,,,Paramount Pictures,Original Screenplay,Drama,Contemporary Fiction,Adrian Lyne,29,5.6,12485\n"
            + "Star Wars Ep. VI: Return of the Jedi,309205079,572700000,32500000,5/25/83,PG,,20th Century Fox,Original Screenplay,Adventure,Science Fiction,Richard Marquand,,,\n"
            + "Octopussy,67900000,187500000,27500000,6/10/83,,,MGM,Based on Book/Short Story,Action,Contemporary Fiction,John Glen,47,6.6,23167\n"
            + "Indiana Jones and the Temple of Doom,179880271,333080271,28000000,5/23/84,,,Paramount Pictures,Original Screenplay,Adventure,Historical Fiction,Steven Spielberg,85,7.5,110761\n"
            + "Ghostbusters,238632124,291632124,30000000,6/8/84,PG,,Sony Pictures,Original Screenplay,Comedy,Science Fiction,Ivan Reitman,,6.8,358\n"
            + "Beverly Hills Cop,234760478,316300000,15000000,12/5/84,,,Paramount Pictures,Original Screenplay,Action,Contemporary Fiction,Martin Brest,83,7.3,45065\n"
            + "Rambo: First Blood Part II,150415432,300400000,44000000,5/22/85,R,,Sony/TriStar,Based on Book/Short Story,Action,Contemporary Fiction,George P. Cosmatos,30,5.8,38548\n"
            + "Back to the Future,210609762,381109762,19000000,7/3/85,,,Universal,Original Screenplay,Adventure,Science Fiction,Robert Zemeckis,96,8.4,201598\n"
            + "Out of Africa,79096868,258210860,31000000,12/18/85,,,Universal,Based on Book/Short Story,Drama,Historical Fiction,Sydney Pollack,63,7,19638\n"
            + "Top Gun,176786701,353786701,15000000,5/16/86,,,Paramount Pictures,Original Screenplay,Action,Contemporary Fiction,Tony Scott,45,6.5,80013\n"
            + "Aliens,85160248,183316455,17000000,7/18/86,R,137,20th Century Fox,Original Screenplay,Action,Science Fiction,James Cameron,100,7.5,84";

    public static final String whiskey = "Name,Rating,Country,Category,Price,ABV,Age,Brand\n"
            + "Canadian Hunter Canadian Whisky,40,Canada,Blended,9,40,,Canadian Hunter\n"
            + "Canadian LTD Blended Canadian Whiskey,43,Canada,Blended,10,,,Canadian LTD\n"
            + "Kellan Irish Whiskey,47,Ireland,Blended,20,40,,Kellan\n"
            + "Rich & Rare Canadian Whisky,47,Canada,Blended,10,,,Rich & Rare\n"
            + "Canadian Mist Blended Canadian Whisky,48,Canada,Blended,12,40,,Canadian Mist\n"
            + "Slane Castle Irish Whiskey,50,Ireland,Blended,25,,,Slane Castle\n"
            + "Canadian Club 6 Year Old Canadian Whisky,53,Canada,Blended,12,40,6,Canadian Club\n"
            + "Origine Irish Whiskey,54,Ireland,Blended,17,,,Origine\n"
            + "Jack Daniel's No. 7 Whiskey,54,USA,Blended,20,40,7,Jack Daniel's\n"
            + "Ellington Canadian Whisky,54,Canada,Blended,11,,,Ellington\n"
            + "Canadian Club Reserve Canadian Whisky,56,Canada,Blended,16,,10,Canadian Club\n"
            + "Pendleton Blended Canadian Whisky,56,Canada,Blended,26,40,,Hood River\n"
            + "Jim Beam Straight Rye Whiskey,57,USA,Rye,15,51,4,Jim Beam\n"
            + "Catoctin Creek Organic Roundstone Rye Whisky,57,USA,Rye,38,40,,Catoctin Creek\n"
            + "Johnnie Walker Red Label Whisky,58,Scotland,Blended,30,40,,Johnnie Walker\n"
            + "Danfield's Private Reserve Small Batch Canaidan Whiskey,60,Canada,Blended,25,,,Danfield\n"
            + "Wiser's Small Batch Canadian Whisky,60,Canada,Rye,29,43,,Wiser's\n"
            + "8 Seconds Canadian Whiskey,64,Canada,Blended,30,40,,8 Seconds\n"
            + "Feckin Irish Whiskey,64,Ireland,Blended,20,40,,Feckin\n"
            + "Bushmills Original Irish Whiskey,65,Ireland,Blended,24,40,,Bushmills\n"
            + "Jameson Irish Whiskey,65,Ireland,Blended,25,40,,Jameson\n"
            + "Templeton Rye Whiskey,67,USA,Rye,35,40,4,Templeton\n"
            + "Dalwhinnie 15 Year Old Single Malt Scotch,67,Scotland,Highlands,50,43,15,Dalwhinnie\n"
            + "Forty Creek John K. Hall Small Batch Reserve Whisky,67,Canada,Blended,60,40,,Forty Creek\n"
            + "Wiser's 18 Year Old Canadian Whisky,67,Canada,Blended,65,40,18,Wiser's\n"
            + "Black Velvet Reserve Canadian Whiskey,67,Canada,Blended,14,40,8,Black Velvet\n"
            + "Crown Royal Canadian Whisky,68,Canada,Blended,25,40,,Crown Royal\n"
            + "Benchmark Old No. 8 Kentucky Straight Bourbon Whiskey,68,USA,Bourbon,12,40,,Benchmark\n"
            + "Forty Creek Barrel Select Canadian Whiskey,69,Canada,Blended,23,40,,Forty Creek\n"
            + "Canadian Club Classic 12 Canadian Whisky,69,Canada,Blended,23,40,12,Canadian Club\n"
            + "Jim Beam Kentucky Straight Bourbon Whiskey,69,USA,Bourbon,15,40,4,Jim Beam\n"
            + "Sazerac Kentucky Straight Rye Whiskey,69,USA,Rye,27,45,6,Sazerac\n"
            + "Famous Grouse Blended Scotch Whisky,69,Scotland,Blended,20,40,,Famous Grouse\n"
            + "Johnnie Walker Black Label Scotch Whisky,70,Scotland,Blended,45,43,12,Johnnie Walker";


    public static String dump(Dataset d) {
        // We ignore the selection field ... the very last one.
        boolean strip = d.fields[d.fields.length - 1].name.equals("#selection");

        List<String> rows = new ArrayList<>();
        rows.add(stripLast(Data.join(d.fields, "|"), strip));
        for (int r = 0; r < d.rowCount(); r++) {
            String[] row = new String[d.fields.length];
            for (int i = 0; i < row.length; i++)
                row[i] = d.fields[i].format(d.fields[i].value(r));
            rows.add(stripLast(Data.join(row, "|"), strip));
        }
        return Data.join(rows, " -- ");
    }

    public static String dump(Field f) {
        String[] items = new String[f.rowCount()];
        for (int i=0; i<items.length;i++) {
            String s = f.valueFormatted(i);
            if (s.equals(Field.VAL_SELECTED)) s = "Y";
            if (s.equals(Field.VAL_UNSELECTED)) s = "N";
            items[i] = s;
        }
        return Data.join(items, ",");
    }


    private static String stripLast(String text, boolean strip) {
        if (!strip) return text;
        int p = text.lastIndexOf("|");
        return text.substring(0, p);
    }
}
