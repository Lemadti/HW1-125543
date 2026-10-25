package pt.ua.deti.tqs.orbit.domain;

/** Compartment sizes (height x width x depth, in cm). A parcel only fits a compartment of the same size. */
public enum CompartmentSize {
    /** Small: 8 x 38 x 64 cm. */
    S("Small", 8, 38, 64),
    /** Medium: 19 x 38 x 64 cm. */
    M("Medium", 19, 38, 64),
    /** Large: 41 x 38 x 64 cm. */
    L("Large", 41, 38, 64);

    private final String label;
    private final int heightCm;
    private final int widthCm;
    private final int depthCm;

    CompartmentSize(String label, int heightCm, int widthCm, int depthCm) {
        this.label = label;
        this.heightCm = heightCm;
        this.widthCm = widthCm;
        this.depthCm = depthCm;
    }

    /**
     * Human-readable name.
     *
     * @return "Small", "Medium" or "Large"
     */
    public String label() {
        return label;
    }

    /**
     * Inner height of the compartment.
     *
     * @return height in centimetres
     */
    public int heightCm() {
        return heightCm;
    }

    /**
     * Inner width of the compartment.
     *
     * @return width in centimetres
     */
    public int widthCm() {
        return widthCm;
    }

    /**
     * Inner depth of the compartment.
     *
     * @return depth in centimetres
     */
    public int depthCm() {
        return depthCm;
    }
}
