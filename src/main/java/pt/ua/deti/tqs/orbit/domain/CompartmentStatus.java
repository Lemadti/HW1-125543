package pt.ua.deti.tqs.orbit.domain;

/** Operational status of the physical compartment, independent of the parcels it holds. */
public enum CompartmentStatus {
    /** The compartment works and can receive new parcels. */
    IN_SERVICE,
    /** The compartment is faulty or under maintenance; no new parcel is allocated to it. */
    OUT_OF_SERVICE
}
