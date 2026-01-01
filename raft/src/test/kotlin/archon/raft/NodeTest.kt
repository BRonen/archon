package archon.raft

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

import archon.moirai.Simulator
import archon.moirai.SimulationContext
import archon.moirai.Event
import archon.moirai.EventPayload

class LibraryTest {
    @Test
    fun foobar() {
        val simulator = Simulator<Timer, Message>(42)

        simulator.registerNode(0) { RaftNode<SimulationContext<Timer, Message>>(it) }
        simulator.registerNode(1) { RaftNode<SimulationContext<Timer, Message>>(it) }

        simulator.queue.add(Event(0, EventPayload.Network(1, Message.Ping()), 100))
        simulator.queue.add(Event(1, EventPayload.Network(0, Message.Ping()), 100))

        simulator.run()
    }
}
