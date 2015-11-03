package org.brunel.color;

/**
 * Defines a class for handling colors
 */
public class Color {

    public static final Color[] boynton = new Color[]{
            new Color("PureBlue", "#0000FF"),
            new Color("PureRed", "#FF0000"),
            new Color("PureGreen", "#00FF00"),
            new Color("PureYellow", "#FFFF00"),
            new Color("PureMagenta", "#FF00FF"),
            new Color("PurePink", "#FF8080"),
            new Color("PureBrown", "#800000"),
            new Color("PureOrange", "#FF8000")
    };

    public static final Color[] kelly = new Color[]{
            new Color("LightBlue", "#A6BDD7"),
            new Color("VividRed", "#C10020"),
            new Color("GrayishYellow", "#CEA262"),
            new Color("MediumGray", "#817066"),
            new Color("VividYellow", "#FFB300"),
            new Color("StrongPurple", "#803E75"),
            new Color("VividOrange", "#FF6800"),
            new Color("VividGreen", "#007D34"),
            new Color("PurplishPink", "#F6768E"),
            new Color("StrongBlue", "#00538A"),
            new Color("StrongPink", "#FF7A5C"),
            new Color("StrongViolet", "#53377A"),
            new Color("OrangeYellow", "#FF8E00"),
            new Color("PurplishRed", "#B32851"),
            new Color("GreenishYellow", "#F4C800"),
            new Color("ReddishBrown", "#7F180D"),
            new Color("YellowishGreen", "#93AA00"),
            new Color("YellowishBrown", "#593315"),
            new Color("ReddishOrange", "#F13A13"),
            new Color("OliveGreen", "#232C16"),
    };

    public static final Color[] general = new Color[]{
            new Color("Maroon", "#800000"),
            new Color("Red", "#ff0000"),
            new Color("Orange", "#ffa500"),
            new Color("Yellow", "#ffff00"),
            new Color("Olive", "#808000"),
            new Color("Purple", "#800080"),
            new Color("Fuschia", "#ff00ff"),
            new Color("White", "#ffffff"),
            new Color("Lime", "#00ff00"),
            new Color("Green", "#008000"),
            new Color("Navy", "#000080"),
            new Color("Blue", "#0000ff"),
            new Color("Aqua", "#00ffff"),
            new Color("Teal", "#008080"),
            new Color("Black", "#000000"),
            new Color("Silver", "#c0c0c0"),
            new Color("Gray", "#808080"),
    };

    public final String name;
    public final String hexCode;

    public Color(String name, String hexCode) {
        this.name = name;
        this.hexCode = hexCode;
    }
}
