package pt.ua.deti.tqs.orbit.domain;

/**
 * Compartment sizes (height x width x depth, in cm). A parcel only fits a compartment of the same size.
 */
public enum CompartmentSize {
    S("Small", 8, 38, 64),
    M("Medium", 19, 38, 64),
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

    public String label() {
        return label;
    }

    public int heightCm() {
        return heightCm;
    }

    public int widthCm() {
        return widthCm;
    }

    public int depthCm() {
        return depthCm;
    }
}
