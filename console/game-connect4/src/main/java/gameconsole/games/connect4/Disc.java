package gameconsole.games.connect4;

public enum Disc {
    RED(1, "rojo", "roja"),
    YELLOW(2, "amarillo", "amarilla");

    private final int prologCode;
    private final String colorName;
    private final String feminineColorName;

    Disc(int prologCode, String colorName, String feminineColorName) {
        this.prologCode = prologCode;
        this.colorName = colorName;
        this.feminineColorName = feminineColorName;
    }

    public int prologCode() {
        return prologCode;
    }

    public String colorName() {
        return colorName;
    }

    public String feminineColorName() {
        return feminineColorName;
    }

    public Disc opposite() {
        return this == RED ? YELLOW : RED;
    }
}
