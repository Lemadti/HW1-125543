package pt.ua.deti.tqs.orbit.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

/** Exhaustive tests over the transition table (oracle: the state machine diagram). */
@DisplayName("ParcelStateMachine — transition table (oracle: the state diagram)")
class ParcelStateMachineTest {

    /** The complete specification: every valid (state, event) pair and its target. */
    private static final Map<String, ParcelState> VALID = Map.ofEntries(
            Map.entry("CREATED/CANCEL", ParcelState.CANCELLED),
            Map.entry("CREATED/DROP_OFF_DEADLINE_PASSED", ParcelState.CANCELLED),
            Map.entry("CREATED/COLLECT", ParcelState.ON_ITS_WAY),
            Map.entry("ON_ITS_WAY/DEPOSIT", ParcelState.READY_FOR_PICKUP),
            Map.entry("ON_ITS_WAY/DROP_OFF_DEADLINE_PASSED", ParcelState.CANCELLED),
            Map.entry("READY_FOR_PICKUP/PICK_UP", ParcelState.PICKED_UP),
            Map.entry("READY_FOR_PICKUP/PICKUP_DEADLINE_PASSED", ParcelState.EXPIRED),
            Map.entry("EXPIRED/PICK_UP", ParcelState.PICKED_UP),
            Map.entry("EXPIRED/MARK_TO_RETURN", ParcelState.TO_RETURN),
            Map.entry("TO_RETURN/PICK_UP", ParcelState.PICKED_UP),
            Map.entry("TO_RETURN/REMOVE", ParcelState.RETURNED));

    static Stream<Arguments> allPairs() {
        return Arrays.stream(ParcelState.values())
                .flatMap(s -> Arrays.stream(ParcelEvent.values()).map(e -> Arguments.of(s, e)));
    }

    @ParameterizedTest(name = "{0} + {1}")
    @MethodSource("allPairs")
    @DisplayName("every (state, event) pair is either the specified transition or rejected")
    void transitionTableMatchesSpecification(ParcelState from, ParcelEvent event) {
        ParcelState expected = VALID.get(from + "/" + event);
        assertThat(ParcelStateMachine.target(from, event)).isEqualTo(java.util.Optional.ofNullable(expected));
        Actor allowedActor = event.allowedActors().iterator().next();
        if (expected == null) {
            assertThatThrownBy(() -> ParcelStateMachine.next(from, event, allowedActor))
                    .isInstanceOf(InvalidTransitionException.class)
                    .hasMessageContaining("not allowed in state " + from);
        } else {
            assertThat(ParcelStateMachine.next(from, event, allowedActor)).isEqualTo(expected);
        }
    }

    @ParameterizedTest(name = "{1} by {2} in {0} is refused")
    @CsvSource({
            "CREATED, CANCEL, COURIER",
            "CREATED, CANCEL, SYSTEM",
            "CREATED, COLLECT, PARTNER",
            "ON_ITS_WAY, DEPOSIT, RECIPIENT",
            "READY_FOR_PICKUP, PICK_UP, COURIER",
            "READY_FOR_PICKUP, PICKUP_DEADLINE_PASSED, OPERATOR",
            "EXPIRED, MARK_TO_RETURN, RECIPIENT",
            "TO_RETURN, REMOVE, PARTNER"})
    @DisplayName("State machine: an actor may only trigger its own events")
    void rejectsActorsThatMayNotTriggerTheEvent(ParcelState from, ParcelEvent event, Actor actor) {
        assertThatThrownBy(() -> ParcelStateMachine.next(from, event, actor))
                .isInstanceOf(InvalidTransitionException.class)
                .hasMessageContaining("may not trigger");
    }

    @ParameterizedTest(name = "{0} is terminal")
    @EnumSource(value = ParcelState.class, names = {"CANCELLED", "PICKED_UP", "RETURNED"})
    @DisplayName("State machine: CANCELLED, PICKED_UP and RETURNED accept no event")
    void terminalStatesAcceptNoEvents(ParcelState state) {
        assertThat(state.isTerminal()).isTrue();
        assertThat(ParcelStateMachine.allowedEvents(state)).isEmpty();
    }

    @Test
    @DisplayName("State machine: a rejected event reports state, event and actor")
    void exceptionCarriesContextForTheErrorBody() {
        InvalidTransitionException e = org.junit.jupiter.api.Assertions.assertThrows(InvalidTransitionException.class,
                () -> ParcelStateMachine.next(ParcelState.PICKED_UP, ParcelEvent.CANCEL, Actor.PARTNER));
        assertThat(e.getCurrentState()).isEqualTo(ParcelState.PICKED_UP);
        assertThat(e.getEvent()).isEqualTo(ParcelEvent.CANCEL);
        assertThat(e.getActor()).isEqualTo(Actor.PARTNER);
    }
}
