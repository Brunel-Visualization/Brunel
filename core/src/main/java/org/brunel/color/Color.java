package org.brunel.color;

/**
 * Defines a class for handling colors
 */
public class Color {

    public static final Color[] boynton = new Color[]{
            new Color("blue", "#0000FF"),
            new Color("red", "#FF0000"),
            new Color("green", "#00FF00"),
            new Color("yellow", "#FFFF00"),
            new Color("magenta", "#FF00FF"),
            new Color("pink", "#FF8080"),
            new Color("gray", "#808080"),
            new Color("brown", "#800000"),
            new Color("orange", "#FF8000")
    };

    public static final Color[] kelly = new Color[]{
            new Color("vivid-yellow", "#FFB300"),
            new Color("strong-purple", "#803E75"),
            new Color("vivid-orange", "#FF6800"),
            new Color("very-light-blue", "#A6BDD7"),
            new Color("vivid-red", "#C10020"),
            new Color("grayish-yellow", "#CEA262"),
            new Color("medium-gray", "#817066"),
            new Color("vivid-green", "#007D34"),
            new Color("strong-purplish-pink", "#F6768E"),
            new Color("strong-blue", "#00538A"),
            new Color("strong-yellowish-pink", "#FF7A5C"),
            new Color("strong-violet", "#53377A"),
            new Color("vivid-orange-yellow", "#FF8E00"),
            new Color("strong-purplish-red", "#B32851"),
            new Color("vivid-greenish-yellow", "#F4C800"),
            new Color("strong-reddish-brown", "#7F180D"),
            new Color("vivid-yellowish-green", "#93AA00"),
            new Color("deep-yellowish-brown", "#593315"),
            new Color("reddish-orange", "#F13A13"),
            new Color("dark-olive-green", "#232C16"),
    };

    public final String name;
    public final String hexCode;

    public Color(String name, String hexCode) {
        this.name = name;
        this.hexCode = hexCode;
    }
}
