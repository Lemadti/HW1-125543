package pt.ua.deti.tqs.orbit.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pt.ua.deti.tqs.orbit.support.InMemoryOrbit;
import pt.ua.deti.tqs.orbit.support.TestData;

/**
 * Compartment allocation rule: an in-service compartment can be allocated if it holds no parcel, or only parcels that
 * no longer hold it (EXPIRED, TO_RETURN, PICKED_UP, CANCELLED, RETURNED). Parcels in CREATED, ON_ITS_WAY or
 * READY_FOR_PICKUP keep it reserved.
 *
 * <p>Each test builds its own fixture (a new {@link InMemoryOrbit} per test instance), puts the old parcel(s) in
 * compartment M-07 and asks the real {@code CompartmentAllocator} for M-07.</p>
 */
@DisplayName("Compartment allocation — which compartments can be reserved")
class CompartmentAllocationPolicyTest {

    private final InMemoryOrbit orbit = new InMemoryOrbit();
    private long nextId = 100;

    /** A parcel in compartment M-07, moved through valid transitions until it reaches the given state. */
    private Parcel oldParcelIn(ParcelState state) {
        Parcel p = TestData.parcel(nextId, "ORB-261024-0" + nextId++, orbit.m07, "hash-" + nextId);
        switch (state) {
            case CREATED -> { }
            case CANCELLED -> p.apply(ParcelEvent.CANCEL, Actor.PARTNER, TestData.T0, null);
            default -> {
                p.apply(ParcelEvent.COLLECT, Actor.COURIER, TestData.T0, null);
                if (state != ParcelState.ON_ITS_WAY) {
                    p.apply(ParcelEvent.DEPOSIT, Actor.COURIER, TestData.T0, null);
                }
                if (state == ParcelState.PICKED_UP) {
                    p.apply(ParcelEvent.PICK_UP, Actor.RECIPIENT, TestData.T0, null);
                }
                if (state == ParcelState.EXPIRED || state == ParcelState.TO_RETURN || state == ParcelState.RETURNED) {
                    p.apply(ParcelEvent.PICKUP_DEADLINE_PASSED, Actor.SYSTEM, TestData.T0, null);
                }
                if (state == ParcelState.TO_RETURN || state == ParcelState.RETURNED) {
                    p.apply(ParcelEvent.MARK_TO_RETURN, Actor.SYSTEM, TestData.T0, null);
                }
                if (state == ParcelState.RETURNED) {
                    p.apply(ParcelEvent.REMOVE, Actor.COURIER, TestData.T0, null);
                }
            }
        }
        orbit.parcels.add(p);
        return p;
    }

    private Compartment allocateM07() {
        return orbit.allocator.allocate(orbit.station, CompartmentSize.M, "M-07");
    }

    private void assertAllocationOfM07IsRejected() {
        assertThatThrownBy(this::allocateM07).isInstanceOfSatisfying(OrbitException.class,
                e -> assertThat(e.getCode()).isEqualTo("COMPARTMENT_NOT_AVAILABLE"));
    }

    // ------------------------------------------------------------------ one old parcel in each state (but RETURNED)

    @Test
    @DisplayName("Compartment allocation: a CREATED parcel keeps the compartment")
    void compartmentWithACreatedParcelIsNotAllocated() {
        // Arrange
        oldParcelIn(ParcelState.CREATED);

        // Act + Assert
        assertAllocationOfM07IsRejected();
    }

    @Test
    @DisplayName("Compartment allocation: an ON_ITS_WAY parcel keeps the compartment")
    void compartmentWithAnOnItsWayParcelIsNotAllocated() {
        // Arrange
        oldParcelIn(ParcelState.ON_ITS_WAY);

        // Act + Assert
        assertAllocationOfM07IsRejected();
    }

    @Test
    @DisplayName("Compartment allocation: a READY_FOR_PICKUP parcel keeps the compartment")
    void compartmentWithAReadyForPickupParcelIsNotAllocated() {
        // Arrange
        oldParcelIn(ParcelState.READY_FOR_PICKUP);

        // Act + Assert
        assertAllocationOfM07IsRejected();
    }

    @Test
    @DisplayName("Compartment allocation: a PICKED_UP parcel frees the compartment")
    void compartmentWithAPickedUpParcelIsAllocated() {
        // Arrange
        oldParcelIn(ParcelState.PICKED_UP);

        // Act
        Compartment allocated = allocateM07();

        // Assert
        assertThat(allocated).isSameAs(orbit.m07);
    }

    @Test
    @DisplayName("Compartment allocation: an EXPIRED parcel frees it and becomes TO_RETURN")
    void compartmentWithAnExpiredParcelIsAllocated() {
        // Arrange
        Parcel expired = oldParcelIn(ParcelState.EXPIRED);

        // Act
        Compartment allocated = allocateM07();

        // Assert
        assertThat(allocated).isSameAs(orbit.m07);
        assertThat(expired.getState()).isEqualTo(ParcelState.TO_RETURN);
    }

    @Test
    @DisplayName("Compartment allocation: a TO_RETURN parcel frees the compartment")
    void compartmentWithAToReturnParcelIsAllocated() {
        // Arrange
        oldParcelIn(ParcelState.TO_RETURN);

        // Act
        Compartment allocated = allocateM07();

        // Assert
        assertThat(allocated).isSameAs(orbit.m07);
    }

    @Test
    @DisplayName("Compartment allocation: a CANCELLED parcel frees the compartment")
    void compartmentWithACancelledParcelIsAllocated() {
        // Arrange
        oldParcelIn(ParcelState.CANCELLED);

        // Act
        Compartment allocated = allocateM07();

        // Assert
        assertThat(allocated).isSameAs(orbit.m07);
    }

    // ------------------------------------------------------------------ two old parcels

    @Test
    @DisplayName("Compartment allocation: RETURNED + READY_FOR_PICKUP keeps it")
    void compartmentWithAReturnedAndAReadyForPickupParcelIsNotAllocated() {
        // Arrange
        oldParcelIn(ParcelState.RETURNED);
        oldParcelIn(ParcelState.READY_FOR_PICKUP);

        // Act + Assert
        assertAllocationOfM07IsRejected();
    }

    @Test
    @DisplayName("Compartment allocation: TO_RETURN + CREATED keeps it")
    void compartmentWithAToReturnAndACreatedParcelIsNotAllocated() {
        // Arrange
        oldParcelIn(ParcelState.TO_RETURN);
        oldParcelIn(ParcelState.CREATED);

        // Act + Assert
        assertAllocationOfM07IsRejected();
    }

    @Test
    @DisplayName("Compartment allocation: TO_RETURN + ON_ITS_WAY keeps it")
    void compartmentWithAToReturnAndAnOnItsWayParcelIsNotAllocated() {
        // Arrange
        oldParcelIn(ParcelState.TO_RETURN);
        oldParcelIn(ParcelState.ON_ITS_WAY);

        // Act + Assert
        assertAllocationOfM07IsRejected();
    }

    // ------------------------------------------------------------------ empty and out of service

    @Test
    @DisplayName("Compartment allocation: an empty compartment is allocated")
    void emptyCompartmentIsAllocated() {
        // Arrange: no parcel was ever in M-07
        assertThat(orbit.parcels).isEmpty();

        // Act
        Compartment allocated = allocateM07();

        // Assert
        assertThat(allocated).isSameAs(orbit.m07);
    }

    @Test
    @DisplayName("Compartment allocation: an out-of-service compartment is never allocated")
    void outOfServiceCompartmentIsNotAllocated() {
        // Arrange
        orbit.m07.putOutOfService("Faulty door");

        // Act + Assert
        assertAllocationOfM07IsRejected();
    }
}
